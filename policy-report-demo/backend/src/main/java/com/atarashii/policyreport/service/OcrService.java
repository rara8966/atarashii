package com.atarashii.policyreport.service;

import com.atarashii.policyreport.config.AppProperties;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {
    private static final Pattern SCANNER_FILE_TOKEN = Pattern.compile("(?i)SKMBT[_A-Z0-9-]*");

    private final AppProperties properties;
    private final OllamaClient ollamaClient;

    public OcrService(AppProperties properties, OllamaClient ollamaClient) {
        this.properties = properties;
        this.ollamaClient = ollamaClient;
    }

    public OcrResult analyze(Path path, String fileName, String contentType, String tikaText) {
        if (!properties.isOcrEnabled()) {
            return OcrResult.notNeeded();
        }
        if (isImage(fileName, contentType)) {
            return ocrImage(path, "上传图片");
        }
        if (isPdf(fileName, contentType) && shouldRunPdfOcr(tikaText)) {
            return ocrPdf(path);
        }
        return OcrResult.notNeeded();
    }

    private OcrResult ocrPdf(Path path) {
        try (PDDocument document = PDDocument.load(path.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            int totalPages = document.getNumberOfPages();
            int pages = Math.min(totalPages, Math.max(1, properties.getOcrMaxPages()));
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();
            int acceptedPages = 0;
            Set<String> providers = new LinkedHashSet<>();
            for (int index = 0; index < pages; index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, Math.max(96, properties.getOcrDpi()), ImageType.RGB);
                Path pageImage = writeTempPng(image, "ocr-page-" + (index + 1));
                OcrTextResult result = generateOcrText(pageImage, ocrPrompt(index + 1, totalPages), toBase64Png(image));
                deleteQuietly(pageImage);
                if (!result.available()) {
                    String detail = text.isEmpty()
                            ? "扫描件疑似需要 OCR，但本地视觉模型未返回有效结果：" + result.rawText()
                            : "扫描件 OCR 已完成前 " + index + " 页，后续页面失败：" + result.rawText();
                    return new OcrResult(true, !text.isEmpty(), !text.isEmpty() ? "warn" : "warn", "OCR扫描件识别", detail, text.toString().trim(), result.model(), index, totalPages);
                }
                if (result.useful()) {
                    acceptedPages++;
                    providers.add(result.model());
                    text.append("\n\n--- OCR 第 ").append(index + 1).append(" 页 ---\n").append(result.text());
                }
            }
            if (text.isEmpty()) {
                return new OcrResult(true, false, "warn", "OCR扫描件识别", "本地视觉模型已尝试 OCR，但未得到可信文字，未并入材料文本。", "", "ollama-vision", pages, totalPages);
            }
            String provider = providers.isEmpty() ? "ocr" : String.join("/", providers);
            String detail = totalPages > pages
                    ? "疑似扫描 PDF，已用 " + provider + " OCR 前 " + pages + "/" + totalPages + " 页，其中 " + acceptedPages + " 页得到可信文字；后续页数可调高 app.ocr-max-pages 后继续。"
                    : "疑似扫描 PDF，已用 " + provider + " 完成 " + pages + " 页 OCR，其中 " + acceptedPages + " 页得到可信文字。";
            return new OcrResult(true, !text.isEmpty(), "pass", "OCR扫描件识别", detail, text.toString().trim(), provider, pages, totalPages);
        } catch (Exception ex) {
            return new OcrResult(true, false, "warn", "OCR扫描件识别", "扫描 PDF OCR 失败：" + ex.getMessage(), "", "ocr", 0, 0);
        }
    }

    private OcrResult ocrImage(Path path, String title) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            OcrTextResult result = generateOcrText(path, ocrPrompt(1, 1), base64);
            if (!result.available()) {
                return new OcrResult(true, false, "warn", "OCR图片识别", title + "需要 OCR，但本地视觉模型未返回有效结果：" + result.rawText(), "", result.model(), 0, 1);
            }
            if (!result.useful()) {
                return new OcrResult(true, false, "warn", "OCR图片识别", title + "已尝试 OCR，但结果不像可信文字，未并入材料文本。", "", result.model(), 1, 1);
            }
            return new OcrResult(true, true, "pass", "OCR图片识别", title + "已用 " + result.model() + " OCR。", result.text(), result.model(), 1, 1);
        } catch (Exception ex) {
            return new OcrResult(true, false, "warn", "OCR图片识别", title + " OCR 失败：" + ex.getMessage(), "", "ocr", 0, 1);
        }
    }

    private boolean shouldRunPdfOcr(String tikaText) {
        String text = tikaText == null ? "" : tikaText;
        String compact = text.replaceAll("\\s+", "");
        int cjk = countCjk(compact);
        int scannerTokens = countScannerTokens(text);
        if (compact.length() < Math.max(200, properties.getOcrMinUsefulChars())) {
            return true;
        }
        if (scannerTokens >= 5 && cjk < properties.getOcrMinUsefulChars()) {
            return true;
        }
        return cjk < 300 && compact.length() < 8_000;
    }

    private int countCjk(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                count++;
            }
        }
        return count;
    }

    private int countScannerTokens(String text) {
        Matcher matcher = SCANNER_FILE_TOKEN.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String cleanOcrText(String text) {
        return (text == null ? "" : text)
                .replace("```", "")
                .replaceAll("(?m)^\\s*OCR[:：]\\s*", "")
                .trim();
    }

    private boolean isUsefulOcrText(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        if (compact.length() < 20) {
            return false;
        }
        int cjk = countCjk(compact);
        double cjkRatio = compact.isEmpty() ? 0 : (double) cjk / compact.length();
        boolean hasMaterialLabel = compact.contains("项目名称")
                || compact.contains("申报面积")
                || compact.contains("供地方式")
                || compact.contains("建设用地")
                || compact.contains("用地面积");
        return (hasMaterialLabel && cjk >= 8) || (cjk >= 40 && cjkRatio >= 0.12);
    }

    private OcrTextResult generateOcrText(Path imagePath, String prompt, String base64Image) {
        String engine = properties.getOcrEngine() == null ? "auto" : properties.getOcrEngine().toLowerCase(Locale.ROOT);
        if (!"vision".equals(engine)) {
            OcrTextResult tesseract = runTesseract(imagePath);
            if (tesseract.useful() || "tesseract".equals(engine)) {
                return tesseract;
            }
        }
        OllamaClient.GenerationResult primary = ollamaClient.generateWithImage(prompt, base64Image);
        OcrTextResult best = toOcrTextResult(primary);
        if (best.useful()) {
            return best;
        }
        String fallbackModel = properties.getOllamaFallbackModel();
        if (primary.usedOllama()
                && fallbackModel != null
                && !fallbackModel.isBlank()
                && !fallbackModel.equals(primary.model())) {
            OcrTextResult fallback = toOcrTextResult(ollamaClient.generateWithImageModel(fallbackModel, prompt, base64Image));
            if (fallback.useful()) {
                return fallback;
            }
        }
        return best;
    }

    private OcrTextResult toOcrTextResult(OllamaClient.GenerationResult result) {
        String text = cleanOcrText(result.text());
        return new OcrTextResult(result.usedOllama(), result.model(), text, result.text(), result.usedOllama() && isUsefulOcrText(text));
    }

    private OcrTextResult runTesseract(Path imagePath) {
        String command = properties.getOcrTesseractCommand();
        if (command == null || command.isBlank()) {
            return new OcrTextResult(false, "tesseract", "", "未配置 tesseract 命令", false);
        }
        Path outputBase = null;
        try {
            Path workDir = Path.of("data", "ocr-work").toAbsolutePath();
            Files.createDirectories(workDir);
            outputBase = Files.createTempFile(workDir, "tesseract-", "");
            Files.deleteIfExists(outputBase);

            List<String> args = new ArrayList<>();
            args.add(command);
            args.add(imagePath.toAbsolutePath().toString());
            args.add(outputBase.toAbsolutePath().toString());
            if (properties.getOcrTesseractLanguage() != null && !properties.getOcrTesseractLanguage().isBlank()) {
                args.add("-l");
                args.add(properties.getOcrTesseractLanguage());
            }
            if (properties.getOcrTesseractDataPath() != null && !properties.getOcrTesseractDataPath().isBlank()) {
                args.add("--tessdata-dir");
                args.add(properties.getOcrTesseractDataPath());
            }
            args.add("--psm");
            args.add(String.valueOf(Math.max(1, properties.getOcrTesseractPageSegMode())));

            Process process = new ProcessBuilder(args).start();
            boolean finished = process.waitFor(Math.max(10, properties.getOcrTesseractTimeoutSeconds()), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new OcrTextResult(false, "tesseract", "", "Tesseract OCR 超时", false);
            }
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            Path textPath = Path.of(outputBase.toAbsolutePath() + ".txt");
            String text = Files.exists(textPath) ? Files.readString(textPath, StandardCharsets.UTF_8) : "";
            deleteQuietly(textPath);
            String cleaned = cleanOcrText(text);
            String model = "tesseract-" + (properties.getOcrTesseractLanguage() == null || properties.getOcrTesseractLanguage().isBlank() ? "default" : properties.getOcrTesseractLanguage());
            return new OcrTextResult(process.exitValue() == 0, model, cleaned, error.isBlank() ? text : error, process.exitValue() == 0 && isUsefulOcrText(cleaned));
        } catch (Exception ex) {
            return new OcrTextResult(false, "tesseract", "", ex.getMessage(), false);
        } finally {
            if (outputBase != null) {
                deleteQuietly(outputBase);
            }
        }
    }

    private Path writeTempPng(BufferedImage image, String prefix) throws Exception {
        Path workDir = Path.of("data", "ocr-work").toAbsolutePath();
        Files.createDirectories(workDir);
        Path path = Files.createTempFile(workDir, prefix, ".png");
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private record OcrTextResult(boolean available, String model, String text, String rawText, boolean useful) {
    }

    private boolean isPdf(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || type.contains("pdf");
    }

    private boolean isImage(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return type.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private String ocrPrompt(int page, int totalPages) {
        return "你是建设用地报批材料 OCR 引擎。请只转写图片中的中文、数字、表格、标题和印章附近可读文字。"
                + "保留原始顺序，表格尽量按行输出；看不清的位置写[不清晰]；不要解释，不要总结。"
                + "当前页: " + page + "/" + totalPages;
    }

    private String toBase64Png(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        }
    }

    public record OcrResult(
            boolean attempted,
            boolean appended,
            String level,
            String title,
            String detail,
            String text,
            String provider,
            int pagesProcessed,
            int totalPages
    ) {
        static OcrResult notNeeded() {
            return new OcrResult(false, false, "info", "", "", "", "", 0, 0);
        }
    }
}