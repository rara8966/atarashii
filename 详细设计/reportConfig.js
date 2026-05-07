var ReportConfig = {
    sections: {
        s1ApprovalSituation: {
            dataKey: 'step1',
            field: 'approvalSituation',
            defaultValue: '1',
            situations: {
                '1': {
                    template: '〔用地预审在批复可行性研究报告（核准）或项目申请报告之后〕：<b>{approvalNote}</b>。',
                    fields: ['approvalNote']
                },
                '2': {
                    template: '〔已超出核准有效期〕：<b>{approvalNote}</b>。',
                    fields: ['approvalNote']
                },
                '3': {
                    template: '〔可研变更〕：<b>{approvalNote}</b>。',
                    fields: ['approvalNote']
                }
            }
        },

        s1DesignChange: {
            dataKey: 'step1',
            field: 'designChange',
            situations: {
                '1': {
                    template: '〔初步设计变更情况〕情形①：<b>{designChangeNote}</b>',
                    fields: ['designChangeNote']
                },
                '2': {
                    template: '〔初步设计变更情况〕情形②：<b>{designChangeNote}</b>',
                    fields: ['designChangeNote']
                }
            },
            noChangeTemplate: ''
        },

        s1ProjectPhase: {
            dataKey: 'step1',
            field: 'projectPhase',
            situations: {
                '1': {
                    template: '〔分期分段报批情况〕情形①：<b>{phaseNote}</b>',
                    fields: ['phaseNote']
                },
                '2': {
                    template: '〔分期分段报批情况〕情形②：<b>{phaseNote}</b>',
                    fields: ['phaseNote']
                }
            }
        },

        s1Forestry: {
            dataKey: 'step1',
            field: 'forestryApproval',
            situations: {
                '1': {
                    template: '项目涉及占用林草部门管理范围内林地，已按要求办理林地相关手续（<b>{forestryApproval}</b>）。',
                    fields: ['forestryApproval']
                },
                '2': {
                    template: '项目不涉及占用林草部门管理范围内林地。',
                    fields: []
                }
            }
        },

        s1SingleProject: {
            dataKey: 'step1',
            field: 'landUseType',
            situations: {
                '1': {
                    template: '项目位于国土空间规划确定的城市和村庄、集镇建设用地范围外，属单独选址建设用地项目。',
                    fields: []
                },
                '2': {
                    template: '项目用地部分位于国土空间规划确定的城市和村庄、集镇建设用地范围外，部分位于城镇村建设用地范围内，按单独选址建设项目用地报批。',
                    fields: []
                }
            }
        },

        s1Construction: {
            dataKey: 'step1',
            field: 'constructionStatus',
            situations: {
                '1': {
                    template: '经我局核查，该项目未动工用地。',
                    fields: []
                },
                '2': {
                    template: '经我局核查，该项目已动工用地，但未超出<b>{advanceDate}</b>经自然资源部同意先行用地范围，或<b>{advanceDate}</b>取得<b>{advanceOrg}</b>同意的临时用地（<b>{advanceNo}</b>）。',
                    fields: ['advanceDate', 'advanceOrg', 'advanceNo']
                },
                '3': {
                    template: '经我局核查，该项目已动工用地，项目{advanceStatus}。',
                    fields: ['advanceStatus']
                }
            }
        },

        s1Reduction: {
            dataKey: 'step1',
            field: 'reductionStatus',
            situations: {
                '1': {
                    template: '该项目用地在省、市、县级审查中未核减用地。',
                    fields: []
                },
                '2': {
                    template: '该项目用地在省、市、县级审查中核减用地<b>{reductionArea}</b>公顷（耕地<b>{reductionCultivated}</b>公顷，含永久基本农田<b>{reductionBasic}</b>公顷）。',
                    fields: ['reductionArea', 'reductionCultivated', 'reductionBasic']
                }
            }
        },

        s2Inconsistency: {
            dataKey: 'step2',
            field: 'caseInconsistency',
            situations: {
                '1': {
                    template: '不存在不一致情况',
                    fields: []
                },
                '2': {
                    template: '<b>不一致情形一</b>〔存在无合法来源建设用地〕：<b>{inconsistencyNote}</b>',
                    fields: ['inconsistencyNote']
                },
                '3': {
                    template: '<b>不一致情形二</b>〔存在已依法批准建设用地〕：<b>{inconsistencyNote}</b>',
                    fields: ['inconsistencyNote']
                },
                '4': {
                    template: '<b>不一致情形三</b>〔其他情况〕：<b>{inconsistencyNote}</b>',
                    fields: ['inconsistencyNote']
                }
            }
        },

        s2Nature53: {
            dataKey: 'step2',
            field: 'caseNature53',
            situations: {
                '1': {
                    template: '不涉及自然资发〔2023〕53号文',
                    fields: []
                },
                '2': {
                    template: '涉及自然资发〔2023〕53号文',
                    fields: []
                }
            }
        },

        s2Flood: {
            dataKey: 'step2',
            field: 'caseFlood',
            situations: {
                '1': {
                    template: '不涉及水利水电项目淹没区',
                    fields: []
                },
                '2': {
                    template: '涉及水利水电项目淹没区',
                    fields: []
                }
            }
        },

        s3Nature: {
            dataKey: 'step3',
            field: 'caseNatureReserve',
            situations: {
                '1': {
                    template: '不位于各级自然保护区。',
                    fields: []
                },
                '2': {
                    template: '该项目穿越/跨越<b>{natureName}</b>自然保护区实验区，已经国家或省级林业和草原主管部门同意，自然保护区范围内不申请用地。',
                    fields: ['natureName']
                },
                '3': {
                    template: '该项目用地/部分用地位于<b>{natureName}</b>自然保护区实验区范围内，用地面积<b>{natureArea}</b>公顷，均位于国家或省级林业和草原主管部门同意的用地范围内。',
                    fields: ['natureName', 'natureArea']
                }
            }
        },

        s3Ecology: {
            dataKey: 'step3',
            field: 'caseEcoRedline',
            situations: {
                '1': {
                    template: '不位于生态保护红线范围内。',
                    fields: []
                },
                '2': {
                    template: '项目穿越/跨越生态保护红线，在生态保护红线范围内不申请用地。',
                    fields: []
                },
                '3': {
                    template: '项目用地涉及生态保护红线，符合生态保护红线内自然保护地核心保护区外，允许的有限人为活动中的<b>{ecologyActivityType}</b>类型，已出具符合生态保护红线内允许有限人为活动认定意见；涉及生态保护红线面积<b>{ecologyArea}</b>公顷，均位于<b>{ecologyProvince}</b>省人民政府出具的认定意见范围内。',
                    fields: ['ecologyActivityType', 'ecologyArea', 'ecologyProvince']
                },
                '4': {
                    template: '项目用地涉及生态保护红线，符合确需占用生态保护红线的国家重大项目类型，已出具不可避让生态保护红线的论证意见；涉及生态保护红线面积<b>{ecologyArea}</b>公顷，均位于<b>{ecologyProvince}</b>省人民政府出具的论证意见范围内。',
                    fields: ['ecologyArea', 'ecologyProvince']
                }
            }
        },

        s3PlanIndex: {
            dataKey: 'step3',
            field: 'casePlan',
            situations: {
                '1': {
                    template: '已列入国家重大项目清单/省级人民政府重大项目用地清单，申请由国家配置计划。',
                    fields: []
                },
                '2': {
                    template: '未纳入国家重大项目清单和省级人民政府重大项目用地清单，按规定使用我省本年度存量土地处置规模为基础核定的计划指标。',
                    fields: []
                }
            }
        },

        s3BasicFarmlandSupplement: {
            dataKey: 'step3',
            field: 'caseBasicFarmland',
            situations: {
                '1': {
                    template: '项目申请占用永久基本农田<b>{basicFarmlandArea}</b>公顷（各功能分区占用永久基本农田情况分别为<b>{basicFarmlandDetails}</b>），占用理由为<b>{basicFarmlandReason}</b>，平均质量状况<b>{basicFarmlandQuality}</b>。<b>{basicFarmlandCounty}</b>自然资源主管部门在可以长期稳定利用的耕地上落实永久基本农田补划任务，完成补划<b>{basicFarmlandSupplement}</b>公顷、平均质量等别<b>{basicFarmlandGrade}</b>，坡度均小于25度。补划耕地质量符合要求，做到了永久基本农田数量不减少、质量不降低。',
                    fields: ['basicFarmlandArea', 'basicFarmlandDetails', 'basicFarmlandReason', 'basicFarmlandQuality', 'basicFarmlandCounty', 'basicFarmlandSupplement', 'basicFarmlandGrade']
                },
                '2': {
                    template: '项目不涉及占用永久基本农田。',
                    fields: []
                }
            }
        },

        s4Occupied: {
            dataKey: 'step4',
            field: 'caseSupplement',
            situations: {
                '1': {
                    template: '已足额补充耕地',
                    fields: []
                },
                '2': {
                    template: '承诺补充耕地',
                    fields: []
                },
                '3': {
                    template: '无法就地补充耕地',
                    fields: []
                }
            }
        },

        s4Paddy: {
            dataKey: 'step4',
            field: 'casePaddy',
            situations: {
                '1': {
                    template: '已足额补充水田',
                    fields: []
                },
                '2': {
                    template: '承诺补充水田',
                    fields: []
                },
                '3': {
                    template: '无法补充水田',
                    fields: []
                }
            }
        },

        s5PublicInterest: {
            dataKey: 'step5',
            field: 'casePublicInterest',
            situations: {
                '1': {
                    template: '该项目征收土地符合《土地管理法》第45条第<b>{lawItem}</b>项规定，属于<b>能源基础设施</b>类建设活动，因<b>{necessity}</b>需要（阐明征收土地的必要性、合理性等情况），确需征收农民集体所有土地。',
                    fields: ['lawItem', 'necessity']
                },
                '2': {
                    template: '该项目征收土地符合《土地管理法》第45条规定，属于<b>交通基础设施</b>类建设活动，因<b>{necessity}</b>需要，确需征收农民集体所有土地。',
                    fields: ['necessity']
                },
                '3': {
                    template: '该项目征收土地符合《土地管理法》第45条规定，属于<b>水利基础设施</b>类建设活动，因<b>{necessity}</b>需要，确需征收农民集体所有土地。',
                    fields: ['necessity']
                },
                '4': {
                    template: '该项目征收土地符合《土地管理法》第45条规定，属于<b>其他公共利益</b>类建设活动，因<b>{necessity}</b>需要，确需征收农民集体所有土地。',
                    fields: ['necessity']
                }
            }
        },

        s5Agreement: {
            dataKey: 'step5',
            field: 'caseAgreement',
            situations: {
                '1': {
                    template: '全部签订征地补偿安置协议',
                    fields: []
                },
                '2': {
                    template: '部分签订征地补偿安置协议（≥90%）',
                    fields: []
                },
                '3': {
                    template: '部分签订征地补偿安置协议（<90%）',
                    fields: []
                }
            }
        },

        s6Industry: {
            dataKey: 'step6',
            field: 'caseIndustry',
            situations: {
                '1': {
                    template: '该项目属于<b>鼓励类</b>建设项目',
                    fields: []
                },
                '2': {
                    template: '该项目属于<b>允许类</b>建设项目',
                    fields: []
                },
                '3': {
                    template: '该项目属于<b>限制类</b>建设项目',
                    fields: []
                }
            }
        },

        s6Supply: {
            dataKey: 'step6',
            field: 'caseSupply',
            situations: {
                '1': {
                    template: '以划拨方式供地',
                    fields: []
                },
                '2': {
                    template: '以出让方式供地',
                    fields: []
                },
                '3': {
                    template: '以租赁方式供地',
                    fields: []
                }
            }
        },

        s6SupplyMethod: {
            dataKey: 'step6',
            field: 'supplyMethod',
            situations: {
                '1': {
                    template: '情形①：项目建设涉及有偿使用新增建设用地<b>{landFeeArea}</b>公顷，涉及新增建设用地<b>{landFeeGrade}</b>、收费标准<b>{landFeeStandard}</b>，共需缴纳新增建设用地土地有偿使用费<b>{landFee}</b>万元。项目所在地<b>{county}</b>人民政府承诺在批准用地后按有关规定及时足额缴纳。',
                    fields: ['landFeeArea', 'landFeeGrade', 'landFeeStandard', 'landFee', 'county']
                },
                '2': {
                    template: '情形②：项目以划拨方式供地，不涉及新增建设用地，按规定不需缴纳新增建设用地土地有偿使用费。',
                    fields: []
                }
            }
        },

        s7Geology: {
            dataKey: 'step7',
            field: 'caseGeo',
            situations: {
                '1': {
                    template: '情形①：该项目建设区<b>是</b>位于地质灾害易发区。已按规定由具备地质灾害危险性评估<b>{evaluationQualification}</b>级资质的<b>{evaluationUnit}</b>进行了地质灾害危险性评估，评估级别为<b>{evaluationLevel}</b>级。评估报告已经专家组审查通过。',
                    fields: ['evaluationQualification', 'evaluationUnit', 'evaluationLevel']
                },
                '2': {
                    template: '情形②：该项目建设区<b>否</b>位于地质灾害易发区。',
                    fields: []
                }
            }
        },

        s7Mineral: {
            dataKey: 'step7',
            field: 'caseMineral',
            situations: {
                '1': {
                    template: '情形①：该项目不压覆重要矿产资源。',
                    fields: []
                },
                '2': {
                    template: '情形②：该项目涉及压覆<b>{mineralName}</b>等重要矿产资源，建设单位已与矿业权人就压矿补偿问题进行协商，有关市、县政府承诺将做好压矿补偿协调工作。',
                    fields: ['mineralName']
                },
                '3': {
                    template: '情形③：该项目涉及压覆<b>{mineralName}</b>等重要矿产资源，建设单位已按规定办理压覆矿产资源审批手续，<b>{mineralOrg}</b>同意压覆上述重要矿产资源。',
                    fields: ['mineralName', 'mineralOrg']
                }
            }
        },

        s8Petition: {
            dataKey: 'step8',
            field: 'petitionType',
            situations: {
                '1': {
                    template: '情形①：经我局核查，该用地不涉及土地征收信访事项。',
                    fields: []
                },
                '2': {
                    template: '情形②：<b>{petitionDate}</b>，<b>{petitionCounty}</b><b>{petitionVillage}</b>村民<b>{petitioner}</b>来信（访）反映该项目<b>{petitionContent}</b>。<b>{petitionOrg}</b>进行了认真调查处理，<b>{petitionHandling}</b>。目前，信访群众反映的问题已得到妥善解决，信访人表示不再上访。',
                    fields: ['petitionDate', 'petitionCounty', 'petitionVillage', 'petitioner', 'petitionContent', 'petitionOrg', 'petitionHandling']
                }
            }
        },

        s8Illegal: {
            dataKey: 'step8',
            field: 'selectedCase',
            defaultValue: '6',
            situations: {
                '1': {
                    template: '情形①：经我局核查，项目未动工，不存在违法用地问题。',
                    fields: []
                },
                '2': {
                    template: '情形②：经我局核查，项目未动工，不存在违法用地问题，但项目用地范围内存在经批准的临时用地。批准临时用地面积<b>{tempArea}</b>公顷，用途为<b>{tempUse}</b>，使用期限为<b>{tempPeriod}</b>；实际用地面积<b>{tempActualArea}</b>公顷、用途为<b>{tempActualUse}</b>，目前在临时使用及土地复垦期限内，符合临时用地批准条件。',
                    fields: ['tempArea', 'tempUse', 'tempPeriod', 'tempActualArea', 'tempActualUse']
                },
                '3': {
                    template: '情形③：经我局核查，项目未动工，不存在项目主体违法用地问题，但项目用地范围内存在非本项目主体的违法用地行为，违法主体为<b>{illegalSubject}</b>，用地面积<b>{illegalArea}</b>公顷，用途为<b>{illegalUse}</b>。<b>{illegalOrg}</b>于<b>{illegalPenaltyDate}</b>对违法用地作出<b>{penaltyType}</b>的处罚决定，各项处罚已于<b>{executeDate}</b>执行到位。',
                    fields: ['illegalSubject', 'illegalArea', 'illegalUse', 'illegalOrg', 'illegalPenaltyDate', 'penaltyType', 'executeDate']
                },
                '4': {
                    template: '情形④：经我局核查，项目未动工，不存在违法用地问题，但项目用地范围内存在非本项目主体的违法用地行为，违法主体为<b>{illegalSubject}</b>，用地面积<b>{illegalArea}</b>公顷，用途为<b>{illegalUse}</b>。<b>{illegalOrg}</b>于<b>{illegalPenaltyDate}</b>对违法用地作出<b>{penaltyType}</b>的处罚决定，各项处罚已于<b>{executeDate}</b>执行到位。<br>违法时间为2020年12月31日以前，并在办理用地预审时所在地县级人民政府承诺在项目报批农用地转用与土地征收前完成处罚，目前承诺已落实到位。',
                    fields: ['illegalSubject', 'illegalArea', 'illegalUse', 'illegalOrg', 'illegalPenaltyDate', 'penaltyType', 'executeDate']
                },
                '5': {
                    template: '情形⑤：经我局核查，项目已于<b>{illegalDate}</b>动工用地，用地面积<b>{illegalArea}</b>公顷，超出<b>{advanceDate}</b>批准的<b>{advanceArea}</b>先行用地范围，存在违法用地问题。<b>{penaltyOrg}</b>于<b>{penaltyDate}</b>对违法用地作出<b>{penaltyType}</b>的处罚决定；各项处罚已于<b>{executeDate}</b>执行到位。相关责任人员<b>{responsiblePerson}</b>受到<b>{punishmentType}</b>。',
                    fields: ['illegalDate', 'illegalArea', 'advanceDate', 'advanceArea', 'penaltyOrg', 'penaltyDate', 'penaltyType', 'executeDate', 'responsiblePerson', 'punishmentType']
                },
                '6': {
                    template: '情形⑥：经我局核查，项目已于<b>{illegalDate}</b>部分/全部动工用地，存在违法用地问题，违法用地面积<b>{illegalArea}</b>公顷，涉及新增建设用地面积<b>{illegalNewArea}</b>公顷，其中农用地<b>{illegalAgri}</b>公顷（耕地<b>{illegalCultivated}</b>公顷）、未利用地<b>{illegalUnused}</b>公顷。项目违法用地涉及生态保护红线或自然保护区。<b>{penaltyOrg}</b>于<b>{penaltyDate}</b>对违法用地作出<b>{penaltyType}</b>的处罚决定；<b>{penaltyOrg2}</b>于<b>{penaltyDate2}</b>对违法用地涉及生态保护红线<b>{ecoRedLineArea}</b>作出<b>{penaltyType2}</b>的从重处罚决定，各项处罚已于<b>{executeDate}</b>执行到位。相关责任人员<b>{responsiblePerson}</b>受到<b>{punishmentType}</b>。',
                    fields: ['illegalDate', 'illegalArea', 'illegalNewArea', 'illegalAgri', 'illegalCultivated', 'illegalUnused', 'penaltyOrg', 'penaltyDate', 'penaltyType', 'penaltyOrg2', 'penaltyDate2', 'ecoRedLineArea', 'penaltyType2', 'executeDate', 'responsiblePerson', 'punishmentType']
                }
            }
        }
    },

    getSituationContent: function(config, data) {
        var fieldValue = data[config.field] || config.defaultValue || '1';
        var situation = config.situations[fieldValue];

        if (!situation) {
            return '';
        }

        var template = situation.template;
        var fields = situation.fields || [];

        fields.forEach(function(f) {
            var value = data[f] || '**';
            template = template.replace('{' + f + '}', value);
        });

        return template;
    },

    hasSituation: function(config, data) {
        var fieldValue = data[config.field];
        return fieldValue && fieldValue !== '' && fieldValue !== '0';
    }
};