package com.atarashii.policyreport.service.verdict;

import java.util.List;
import java.util.Map;

/**
 * 功能区合规判定的抽象入口。
 * <p>
 * 故意把判定结果的数据结构（{@link ZoneVerdict} 等）也放在接口里：
 * 这样客户端构建（剔除了真正的算法类 {@code FunctionalZoneVerdictService}）
 * 仍持有这些类型，可用于反序列化服务器返回的 JSON。
 * <p>
 * 两个实现：
 * <ul>
 *   <li>{@code FunctionalZoneVerdictService} —— 真正的查表+数值比对算法，只在服务器构建中存在；</li>
 *   <li>{@code RemoteZoneVerdictService} —— 客户端构建用，HTTP 调用服务器，本地无算法。</li>
 * </ul>
 */
public interface ZoneVerdictApi {

    /** 对一个项目做按功能区分组的合规判定。 */
    List<ZoneVerdict> verdictForProject(String projectId);

    /** 单个指标的比对结论。verdict 取值 PASS / FAIL / WARN / UNKNOWN。 */
    record IndicatorVerdict(
            String indicatorName,
            String standardValue,
            String actualValue,
            String semantic,
            String verdict,
            Double deltaPct,
            String note,
            String sourceFileId
    ) {
    }

    /** 单张标准表的判定结果。 */
    record TableVerdict(
            String tableId,
            String tableCode,
            String tableTitle,
            Map<String, String> matchedQueryKeys,
            int matchedRowIndex,
            List<IndicatorVerdict> indicators
    ) {
    }

    /** 一个功能区的判定结果。 */
    record ZoneVerdict(
            String functionalZone,
            int matchedTables,
            int totalAnnotatedTables,
            int projectFieldCount,
            List<TableVerdict> tables
    ) {
    }
}
