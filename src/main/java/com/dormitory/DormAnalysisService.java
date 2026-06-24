package com.dormitory;

public class DormAnalysisService {
    private final LocalRuleDormAnalyzer localAnalyzer;

    public DormAnalysisService() {
        this.localAnalyzer = new LocalRuleDormAnalyzer();
    }

    public String analyze(DormStatistics statistics) {
        OpenAiCompatibleDormAnalyzer remoteAnalyzer = new OpenAiCompatibleDormAnalyzer();
        if (remoteAnalyzer.isConfigured()) {
            try {
                return remoteAnalyzer.analyze(statistics);
            } catch (RuntimeException e) {
                return "智能分析服务暂时不可用，系统已切换为本地分析模式。建议稍后重试或联系管理员检查模型服务配置。"
                        + "\n" + localAnalyzer.analyze(statistics);
            }
        }
        return localAnalyzer.analyze(statistics)
                + "\n当前为本地规则分析模式。如需启用大模型生成更完整的运营建议，请由管理员在系统设置中选择一个模型配置。";
    }
}
