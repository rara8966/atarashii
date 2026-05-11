// 材料清单规格（八步审查的必传/选传材料）
// 来自 LegacyApp.tsx 的 MATERIAL_SPECS 常量，抽成独立模块供新版 Workbench 复用。
// 注意：condition 字段（'cultivatedLand' / 'forest' / 'petition'）旧版用于配合 step.situations
// 做条件必传判定；v2 工作台暂未实现情形选择 UI，运行时一律按 required 判定，等后续补 CaseSelectors 再启用。

export type MaterialSpec = {
  id: string;
  label: string;
  required: boolean;
  sub: string;
  keywords: string[];
  condition?: 'cultivatedLand' | 'forest' | 'petition';
};

function spec(id: string, label: string, required: boolean, sub: string, keywords: string[], condition?: MaterialSpec['condition']): MaterialSpec {
  return { id, label, required, sub, keywords, condition };
}

export const MATERIAL_SPECS: Record<number, MaterialSpec[]> = {
  1: [
    spec('pre', '建设用地预审批复', true, '法定前置条件', ['预审', '用地预审', '预审批复']),
    spec('approval', '项目立项批复文件', true, '核准/备案/立项', ['核准', '立项', '备案', '项目批复']),
    spec('design', '初步设计批复文件', false, '建设规模和标准，选传', ['初步设计', '初设']),
    spec('approvalChange', '可行性研究报告变更批复', false, '有变更时上传', ['可研', '可行性研究', '变更批复']),
    spec('designChange', '初步设计变更批复', false, '有初设变更时上传', ['初设变更', '初步设计变更'])
  ],
  2: [
    spec('survey', '勘测定界报告', true, '面积和界址来源', ['勘测', '定界']),
    spec('landClass', '土地分类权属面积汇总表', true, '地类面积核对', ['分类', '权属面积', '汇总表']),
    spec('ownership', '权属情况汇总表', true, '国有/集体权属', ['权属', '所有权', '使用权']),
    spec('landChangeOverlay', '年度国土变更调查套合情况分析', true, '年度变更调查套合', ['年度国土变更', '国土变更调查', '套合情况', '套合分析']),
    spec('illegalBuilt', '已批准建设用地来源材料', false, '存在建设用地时上传', ['批准建设用地', '合法来源']),
    spec('flood', '水利水电淹没区说明', false, '涉及时上传', ['淹没区', '水利水电'])
  ],
  3: [
    spec('plan', '规划佐证材料', true, '国土空间规划符合性', ['规划', '三区三线', '用途管制']),
    spec('quota', '年度计划指标文件', true, '计划指标来源', ['计划指标', '指标配置']),
    spec('forest', '林地批复', true, '涉及林地时条件必传', ['林地', '林草'], 'forest'),
    spec('eco', '生态/保护地意见', false, '涉及红线或保护区时上传', ['生态保护红线', '自然保护区', '保护地'])
  ],
  4: [
    spec('supplement', '补充耕地方案审查表', true, '占用耕地时必传，本项目不占耕地无需上传', ['补充耕地', '方案审查'], 'cultivatedLand'),
    spec('balance', '耕地占补平衡挂钩信息单', true, '占用耕地时必传，本项目不占耕地无需上传', ['占补平衡', '挂钩信息'], 'cultivatedLand')
  ],
  5: [
    spec('notice', '征收土地预公告', true, '征地程序起点', ['预公告', '征收土地预公告']),
    spec('survey', '土地现状调查材料', true, '现状确认', ['土地现状调查', '现状调查材料', '现状调查']),
    spec('risk', '社会稳定风险评估报告', true, '社稳/稳评程序', ['社稳', '社稳报告', '稳评', '社会稳定风险']),
    spec('agreement', '征地补偿安置公告及照片', true, '公告及照片', ['征地补偿安置公告', '补偿安置公告', '公告照片', '照片']),
    spec('hearing', '听证材料', true, '听证告知、笔录或放弃听证', ['听证', '听证告知', '听证笔录', '放弃听证']),
    spec('socialSecurity', '社保审核意见', true, '被征地农民保障', ['社保', '社会保障'])
  ],
  6: [
    spec('intensive', '节约集约用地论证分析专章', false, '超指标时必传', ['节约集约', '论证分析专章']),
    spec('industry', '行业主管部门意见', false, '超标准或特殊项目上传', ['行业主管', '主管部门意见']),
    spec('fee', '土地有偿使用费材料', true, '新增建设用地费用', ['有偿使用费', '缴纳', '缴库'])
  ],
  7: [
    spec('geo', '地灾评估报告批复', true, '地灾易发区核验', ['地质灾害', '地灾评估', '地灾批复']),
    spec('mine', '压覆矿查询表', false, '压覆矿产结论，选传', ['压覆矿查询', '压覆矿', '压覆', '矿产', '压矿']),
    spec('mineApproval', '压覆审批/补偿材料', false, '涉及压覆时上传', ['压覆审批', '补偿协议'])
  ],
  8: [
    spec('petition', '信访处理说明', false, '有信访事项时条件选传', ['信访', '来信', '上访']),
    spec('illegal', '违法用地查处案卷', false, '存在违法用地时条件选传', ['违法用地', '行政处罚', '查处']),
    spec('rectification', '查处到位意见书', false, '需证明处罚执行到位时条件选传', ['查处到位', '整改到位', '罚款到账'])
  ]
};

export type ExtractedFieldRaw = { label?: string; key?: string; value?: string; source?: string; confidence?: number; sourceFileId?: string | null };

// 解析 FileAnalysisEntity.extractedFieldsJson（JSON 字符串）为字段数组
export function parseExtractedFields(json: string | null | undefined): ExtractedFieldRaw[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    if (Array.isArray(parsed)) return parsed as ExtractedFieldRaw[];
    return [];
  } catch {
    return [];
  }
}

// v2 适配版的材料匹配：基于文件名 + 检测类型 + 抽取字段文本，做关键词包含匹配
export function matchesMaterialV2(file: { originalName: string }, detail: { detectedDocumentType?: string; extractedFieldsJson?: string; extractedText?: string } | null | undefined, material: MaterialSpec): boolean {
  const fields = parseExtractedFields(detail?.extractedFieldsJson);
  const fieldText = fields.map((f) => `${f.label ?? f.key ?? ''} ${f.value ?? ''}`).join(' ');
  const textPreview = (detail?.extractedText ?? '').slice(0, 800);
  const haystack = `${file.originalName} ${detail?.detectedDocumentType ?? ''} ${textPreview} ${fieldText}`.toLowerCase();
  return material.keywords.some((keyword) => haystack.includes(keyword.toLowerCase()));
}

// ── 情形选择（CaseSelectors） ──
// 来自 LegacyApp 的 CASE_GROUPS。每组有若干 option（顺序为 "1","2","3"...）。
// 用户选择后，结合 spec.condition 判定材料是否需要上传（见 isMaterialSkipped）。

export type CaseOption = { value: string; label: string };
export type CaseGroup = { id: string; title: string; options: CaseOption[] };

function group(id: string, title: string, labels: string[]): CaseGroup {
  return { id, title, options: labels.map((label, i) => ({ value: String(i + 1), label })) };
}

export const CASE_GROUPS: Record<number, CaseGroup[]> = {
  1: [
    group('approvalSituation', '可研批复情形选择', ['预审在可研批复后 已出具检讨', '已超核准有效期 已延期', '可研变更 已批复', '无可研/核准变更']),
    group('designChange', '初步设计变更情形选择', ['普通变更 已批复', '审批权下放 地方办理', '无初步设计变更']),
    group('projectPhase', '分期/分段报批情形选择', ['分段报批 多城市', '分期报批 已确定期数']),
    group('landUseType', '单独选址情形选择', ['完全在规划范围外', '部分在规划范围内', '符合规划范围']),
    group('forestryApproval', '林地审批情形选择', ['涉及林地 已审批', '不涉及林地']),
    group('constructionStatus', '动工用地情形选择', ['未动工，不存在违法用地问题', '未动工，但项目范围内存在经批准的临时用地', '已动工，未超出经批准的先行用地范围', '项目主体未动工，但存在非本项目主体的违法用地行为', '已动工，超出经批准的先行用地范围', '已动工，存在违法用地问题']),
    group('reductionStatus', '核减用地情形选择', ['未核减用地', '已核减用地'])
  ],
  2: [
    group('caseInconsistency', '国土变更调查套合情况', ['与实际申请用地情况一致', '存在无合法来源建设用地', '存在已依法批准建设用地', '其他不一致情况']),
    group('caseNature53', '自然资发〔2023〕53号文', ['不涉及该文件', '涉及该文件']),
    group('caseFlood', '水利水电项目淹没区', ['不涉及淹没区', '涉及淹没区'])
  ],
  3: [
    group('caseNatureReserve', '自然保护区情形选择', ['不位于自然保护区', '穿越/跨越保护区 不申请用地', '用地位于实验区 已同意']),
    group('caseEcoRedline', '生态保护红线情形选择', ['不位于生态保护红线', '穿越/跨越红线 不申请用地', '有限人为活动 已认定', '国家重大项目 不可避让']),
    group('casePlan', '计划指标配置情形选择', ['国家/省级重大项目配置', '使用省级存量处置规模指标']),
    group('caseBasicFarmland', '永久基本农田补划情形选择', ['占用永农 已补划', '不涉及占用永久基本农田'])
  ],
  4: [
    group('caseSupplement', '耕地补充情形选择', ['不涉及占用耕地', '已足额补充耕地', '承诺补充耕地', '无法就地补充耕地']),
    group('casePaddy', '水田补充情形选择', ['不涉及占用水田', '已足额补充水田', '承诺补充水田', '无法补充水田'])
  ],
  5: [
    group('casePublicInterest', '征地公共利益情形选择', ['能源基础设施', '交通基础设施', '水利基础设施', '其他公共利益']),
    group('caseAgreement', '补偿协议签订率情形选择', ['全部签订', '部分签订 ≥90%', '部分签订 <90%'])
  ],
  6: [
    group('caseIndustry', '产业政策分类情形选择', ['鼓励类建设项目', '允许类建设项目', '限制类建设项目']),
    group('caseSupply', '供地方式情形选择', ['划拨方式供地', '出让方式供地', '租赁方式供地']),
    group('supplyMethod', '有偿使用费情形选择', ['涉及新增建设用地 已测算费用', '划拨且不涉及新增建设用地'])
  ],
  7: [
    group('caseGeo', '地质灾害评估情形选择', ['位于易发区 已评估', '不在易发区']),
    group('caseMineral', '压覆矿产情形选择', ['不压覆重要矿产', '压覆 已协商补偿', '压覆 已办理审批'])
  ],
  8: [
    group('petitionType', '信访处理情形选择', ['无信访事项', '涉及信访 已妥善处理']),
    group('selectedCase', '违法用地情形选择', ['未动工无违法', '未动工 有临时用地', '范围内他人违法 已处罚', '2020年前违法 已处罚承诺履行', '已动工超先行用地 已处罚', '涉及生态红线/保护区 从重处罚'])
  ]
};

// situations: Map<groupId, value>（合并所有步骤的 group → value）
export type SituationMap = Record<string, string>;

// 判断该 spec 是否被当前情形跳过（无需上传）。
// 与 LegacyApp 的 hasPositiveCondition 等价：检查对应 group 的 option 标签是否含正向关键词。
export function isMaterialSkipped(material: MaterialSpec, situations: SituationMap, allGroups: CaseGroup[]): boolean {
  if (!material.condition) return false;
  if (material.condition === 'cultivatedLand') {
    return !hasPositiveOption(situations, allGroups, 'caseSupplement', ['占用耕地', '耕地']);
  }
  if (material.condition === 'forest') {
    return !hasPositiveOption(situations, allGroups, 'forestryApproval', ['林地']);
  }
  if (material.condition === 'petition') {
    return !hasPositiveOption(situations, allGroups, 'petitionType', ['信访事项', '信访']);
  }
  return false;
}

export function isMaterialRequired(material: MaterialSpec, situations: SituationMap, allGroups: CaseGroup[]): boolean {
  return material.required && !isMaterialSkipped(material, situations, allGroups);
}

function hasPositiveOption(situations: SituationMap, allGroups: CaseGroup[], groupId: string, labels: string[]): boolean {
  const value = situations[groupId];
  if (!value) return true; // 未选则默认按"涉及"处理（保守 — 显示为必传）
  const groupDef = allGroups.find(g => g.id === groupId);
  if (!groupDef) return true;
  const opt = groupDef.options.find(o => o.value === value);
  if (!opt) return true;
  if (isNegativeLabel(opt.label)) return false;
  return labels.some(label => opt.label.includes(label));
}

function isNegativeLabel(label: string): boolean {
  return /^不|^未|^无/.test(label) || label.includes('不涉及') || label.includes('不存在') || label.includes('不在');
}

export function flattenAllGroups(): CaseGroup[] {
  return Object.values(CASE_GROUPS).flat();
}
