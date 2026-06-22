package com.dormitory;

public class DormAnalysisService {
    private final OpenAiCompatibleDormAnalyzer remoteAnalyzer;
    private final LocalRuleDormAnalyzer localAnalyzer;

    public DormAnalysisService() {
        this.remoteAnalyzer = new OpenAiCompatibleDormAnalyzer();
        this.localAnalyzer = new LocalRuleDormAnalyzer();
    }

    public String analyze(DormStatistics statistics) {
        if (remoteAnalyzer.isConfigured()) {
            try {
                return remoteAnalyzer.analyze(statistics);
            } catch (RuntimeException e) {
                return "大模型接口调用失败，系统已启用本地规则分析。原因：" + e.getMessage()
                        + "\n" + localAnalyzer.analyze(statistics);
            }
        }
        return localAnalyzer.analyze(statistics)
                + "\n提示：未配置 LLM_API_URL/LLM_API_KEY/LLM_MODEL，当前为本地规则分析；配置后系统会调用大模型接口。";
    }
}
