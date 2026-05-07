var StepApp = {
    currentStep: 1,
    
    init: function(step) {
        this.currentStep = step;
        console.log('✅ 步骤 ' + step + ' 页面已加载');
    },

    saveStepData: function(step) {
        console.log('请在各自页面中实现 saveStepData 函数');
        return {};
    },

    saveAndNext: function(step) {
        if (typeof saveStepData === 'function') {
            saveStepData(step);
        }
        var nextStep = step + 1;
        if (nextStep <= 8) {
            window.location.href = '第' + nextStep + '步页面设计.html';
        } else {
            alert('已是最后一步！');
        }
    },

    previewReport: function() {
        localStorage.setItem('fromPage', this.getCurrentPageName());
        window.location.href = '兴宁五塘风电场一期_审查报告.md';
    },

    goBack: function() {
        var fromPage = localStorage.getItem('fromPage');
        if (fromPage) {
            localStorage.removeItem('fromPage');
            window.location.href = fromPage;
        } else {
            window.location.href = '第1步页面设计.html';
        }
    },

    getCurrentPageName: function() {
        var path = window.location.pathname;
        var filename = path.split('/').pop();
        return filename;
    },

    showToast: function(message, type) {
        type = type || 'success';
        var toast = document.createElement('div');
        toast.className = 'toast toast-' + type;
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(function() {
            toast.remove();
        }, 3000);
    },

    formatDate: function(date) {
        var d = new Date(date);
        var year = d.getFullYear();
        var month = String(d.getMonth() + 1).padStart(2, '0');
        var day = String(d.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    },

    exportToJson: function() {
        var allData = {};
        for (var i = 1; i <= 8; i++) {
            var stepData = localStorage.getItem('step' + i + 'Data');
            if (stepData) {
                allData['step' + i] = JSON.parse(stepData);
            }
        }
        allData.exportTime = new Date().toISOString();
        allData.projectName = allData.step1 ? allData.step1.projectName : '未命名项目';

        var jsonStr = JSON.stringify(allData, null, 2);
        var blob = new Blob([jsonStr], { type: 'application/json' });
        var url = URL.createObjectURL(blob);

        var a = document.createElement('a');
        a.href = url;
        a.download = '建设用地报批数据.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        console.log('✅ 数据已导出到: 建设用地报批数据.json');
        alert('✅ 数据已导出！\n\n文件名: 建设用地报批数据.json\n项目: ' + allData.projectName + '\n导出时间: ' + new Date().toLocaleString());
    },

    importFromJson: function(file) {
        var reader = new FileReader();
        reader.onload = function(e) {
            try {
                var allData = JSON.parse(e.target.result);
                for (var i = 1; i <= 8; i++) {
                    if (allData['step' + i]) {
                        localStorage.setItem('step' + i + 'Data', JSON.stringify(allData['step' + i]));
                    }
                }
                console.log('✅ 数据已导入:', allData);
                alert('✅ 数据导入成功！\n\n项目: ' + (allData.projectName || '未命名') + '\n导入时间: ' + new Date().toLocaleString());
                location.reload();
            } catch (err) {
                console.error('导入失败:', err);
                alert('❌ 导入失败！文件格式不正确。');
            }
        };
        reader.readAsText(file);
    },

    loadFromJsonFile: function() {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json';
        input.onchange = function(e) {
            if (e.target.files && e.target.files[0]) {
                StepApp.importFromJson(e.target.files[0]);
            }
        };
        input.click();
    }
};

function saveAndNext(step) {
    StepApp.saveAndNext(step);
}

function previewReport() {
    StepApp.previewReport();
}

function goBack() {
    StepApp.goBack();
}

function showToast(message, type) {
    StepApp.showToast(message, type);
}

function exportToJson() {
    StepApp.exportToJson();
}

function loadFromJsonFile() {
    StepApp.loadFromJsonFile();
}

function clearAllData() {
    if (confirm('⚠️ 确定要清空所有数据吗？\n\n此操作将删除：\n- 所有8个步骤的填报数据\n- 本地缓存数据\n\n清空后无法恢复，请先确认已导出数据！')) {
        for (var i = 1; i <= 8; i++) {
            localStorage.removeItem('step' + i + 'Data');
        }
        localStorage.removeItem('fromPage');
        console.log('🗑️ 所有数据已清空');
        alert('✅ 所有数据已清空！\n\n请刷新页面重新开始填报。');
        location.reload();
    }
}
