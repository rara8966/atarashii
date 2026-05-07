// 兴宁五塘风电场一期工程真实数据初始化
function initRealData() {
    // 步骤1：项目基本情况
    var step1Data = {
        projectName: "兴宁五塘风电场一期工程",
        projectCode: "2025-450000-44-01-008976",
        constructionUnit: "广西兴宁风电开发有限公司",
        projectType: "核准",
        totalInvestment: "4.22",
        scale: "装机容量50MW，安装8台6.25MW风力发电机组及配套箱式变电站",
        preApprovalNumber: "450101202300039号",
        approvalNumber: "南发改能源〔2023〕12号",
        designApproval: "8台6.25MW风力发电机组，配套8台6900KVA箱式变电站",
        projectLocation: "南宁市兴宁区五塘镇",
        landUseType: "单独选址建设用地项目",
        forestryApproval: "桂林审准资南宁[2024]102号",
        constructionStatus: "已动工建设中",
        reductionStatus: "未核减用地",
        // 新增：预审规模变化信息
        preApprovalScale: "0.4748",
        actualScale: "0.3800",
        scaleChangeReason: "项目优化调整为8台6.25MW机组"
    };

    // 步骤2：申请用地现状
    var step2Data = {
        surveyUnit: "南宁市自然资源信息集团有限公司",
        qualificationNo: "TD/T1008-2007",
        county: "南宁市兴宁区",
        towns: "五塘镇",
        villages: "五塘社区",
        stateUnits: "广西壮族自治区国有高峰林场",
        totalParcels: "2",
        registeredParcels: "1",
        unregisteredParcels: "1",
        ownershipDispute: "土地产权明晰，界址清楚，没有争议",
        agriculturalLand: "0.3800",
        cultivatedLand: "0",
        basicFarmland: "0",
        constructionLand: "0",
        unusedLand: "0",
        collectiveLand: "0.0236",
        stateLand: "0.3564",
        // 新增：国有土地明细
        stateLandDetails: "乔木林地0.3281公顷、其他林地0.0283公顷",
        stateLandAgreement: "已取得广西壮族自治区国有高峰林场同意"
    };

    // 步骤3：农用地转用情况
    var step3Data = {
        agriculturalArea: "0.3800",
        cultivatedArea: "0",
        basicFarmlandArea: "0",
        unusedArea: "0",
        basicFarmlandApply: "0",
        basicFarmlandQuality: "-",
        supplementArea: "-",
        supplementQuality: "-",
        surveyDate: "2025年03月15日",
        conversionArea: "0.3800",
        planCompliance: "符合南宁市国土空间总体规划",
        ecologicalRedLine: "不涉及生态保护红线",
        natureReserve: "不位于自然保护区",
        landUsePlan: "自治区核销",
        preApprovalScale: "0.4748公顷",
        preApprovalChangeReason: "项目优化调整为8台6.25MW机组"
    };

    // 步骤4：补充耕地情况
    var step4Data = {
        occupiedCultivatedArea: "0",
        occupiedPaddyArea: "0",
        managementMethod: "不涉及占用耕地",
        supplementInfoNo: "-",
        supplementedArea: "-",
        supplementedPaddyScale: "-",
        supplementedYield: "-",
        supplementProjectNo: "-",
        supplementCost: "-",
        balanceStatus: "无补充耕地任务"
    };

    // 步骤5：土地征收情况
    var step5Data = {
        zoneCount: "1",
        compensationRange: "8万元/亩",
        socialSecurityFee: "已到位",
        expropriationArea: "0.0236",
        publicInterestCompliance: "符合公共利益",
        preWorkStatus: "已完成",
        compensationStandard: "一般农用地8万元/亩",
        socialSecurityNumber: "5人",
        agreementStatus: "已全部签订"
    };

    // 步骤6：土地利用情况
    var step6Data = {
        industryPolicy: "符合国家产业政策",
        restrictedLand: "不涉及限制用地项目",
        prohibitedLand: "不涉及禁止用地项目",
        projectType: "能源项目",
        constructionStandard: "电力工程项目建设用地指标（风电场）",
        constructionContent: "8台6.25MW风力发电机组及配套箱式变电站",
        landStandard: "符合规定（风电机组0.36公顷，箱变0.02公顷）",
        supplyMethod: "出让方式供地",
        landUseType: "工业用地",
        floorAreaRatio: "符合指标",
        intensiveEvaluation: "达到同行业先进水平",
        illegalLandArea: "0.0588公顷",
        landFee: "24.32万元",
        // 新增：土地有偿使用费详情
        landFeeGrade: "五等",
        landFeeStandard: "64万元/公顷",
        landFeeArea: "0.3800",
        // 新增：计划指标来源
        planIndexSource: "自治区核销"
    };

    // 步骤7：地质灾害与压覆矿产
    var step7Data = {
        evaluationUnit: "中核大地生态科技有限公司",
        evaluationQualification: "甲级",
        evaluationLevel: "一级",
        evaluationDate: "2025年02月28日",
        expertReviewDate: "2025年03月10日",
        geologyConclusion: "位于地质灾害易发区，需配套建设地质灾害防治工程",
        mineralOverlap: "不压覆重要矿产资源",
        mineralEvaluation: "不涉及重要矿产"
    };

    // 步骤8：信访与违法用地
    var step8Data = {
        selectedCase: "6",
        petitionStatus: "不涉及信访事项",
        illegalArea: "0.0588",
        illegalType: "风机塔筒0.0308公顷，箱式变压器0.0256公顷，隔离挡墙0.0024公顷",
        rectificationStatus: "已整改到位",
        penaltyNumber: "南兴综执两违土地罚决字[2025]第11号",
        rectificationMeasures: "责令退还非法占用土地，没收建筑物，罚款已到位"
    };

    // 保存到 localStorage
    localStorage.setItem('step1Data', JSON.stringify(step1Data));
    localStorage.setItem('step2Data', JSON.stringify(step2Data));
    localStorage.setItem('step3Data', JSON.stringify(step3Data));
    localStorage.setItem('step4Data', JSON.stringify(step4Data));
    localStorage.setItem('step5Data', JSON.stringify(step5Data));
    localStorage.setItem('step6Data', JSON.stringify(step6Data));
    localStorage.setItem('step7Data', JSON.stringify(step7Data));
    localStorage.setItem('step8Data', JSON.stringify(step8Data));

    console.log('✅ 真实数据已初始化完成！');
    alert('✅ 真实数据已成功加载！\n\n项目：兴宁五塘风电场一期工程\n数据来源：县级审查报告.txt\n\n请刷新页面查看效果。');
}

// 页面加载时自动执行
if (typeof window !== 'undefined') {
    window.onload = function() {
        // 如果没有数据，则初始化
        if (!localStorage.getItem('step1Data')) {
            initRealData();
        }
    };
}

// 暴露全局函数
if (typeof window !== 'undefined') {
    window.initRealData = initRealData;
}