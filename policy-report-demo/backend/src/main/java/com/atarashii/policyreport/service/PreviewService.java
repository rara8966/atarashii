package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.PreviewSnapshotEntity;
import com.atarashii.policyreport.persistence.PreviewSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PreviewService {
    private final PreviewSnapshotRepository snapshotRepository;
    private final AsyncPreviewWorker asyncPreviewWorker;

    public PreviewService(PreviewSnapshotRepository snapshotRepository,
                          AsyncPreviewWorker asyncPreviewWorker) {
        this.snapshotRepository = snapshotRepository;
        this.asyncPreviewWorker = asyncPreviewWorker;
    }

    @Transactional
    public PreviewSnapshotEntity initiate(String projectId) {
        // upsert 模式：复用现有记录而非 delete+insert，避免 Hibernate action ordering
        // 把 INSERT 排在 DELETE 之前导致 project_id unique 约束冲突（500）。
        PreviewSnapshotEntity snapshot = snapshotRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    PreviewSnapshotEntity fresh = new PreviewSnapshotEntity();
                    fresh.setProjectId(projectId);
                    return fresh;
                });
        snapshot.setStatus("GENERATING");
        snapshot.setSnapshotData(null);
        snapshot.setGeneratedAt(null);
        snapshot.setConfirmedAt(null);
        snapshotRepository.save(snapshot);
        asyncPreviewWorker.generateAsync(projectId, snapshot.getId());
        return snapshot;
    }

    public PreviewSnapshotEntity getSnapshot(String projectId) {
        return snapshotRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("预览尚未生成，请先点击生成预览"));
    }

    @Transactional
    public PreviewSnapshotEntity confirm(String projectId) {
        PreviewSnapshotEntity snap = getSnapshot(projectId);
        if (!"READY".equals(snap.getStatus())) throw new IllegalStateException("预览尚未就绪");
        snap.setStatus("CONFIRMED");
        snap.setConfirmedAt(LocalDateTime.now());
        return snapshotRepository.save(snap);
    }
}
