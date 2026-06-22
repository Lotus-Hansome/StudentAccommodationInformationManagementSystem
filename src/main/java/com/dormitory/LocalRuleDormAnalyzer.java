package com.dormitory;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public class LocalRuleDormAnalyzer implements DormAnalyzer {
    @Override
    public String analyze(DormStatistics statistics) {
        if (statistics.getTotalStudents() == 0) {
            return statistics.getScopeType() + statistics.getScopeValue()
                    + "暂无入住数据。请先在“住宿信息”中添加该范围的学生记录，或选择已有入住数据的楼栋/宿舍后再生成评估。";
        }

        Optional<Map.Entry<String, Integer>> topDepartment = statistics.getDepartmentCounts().entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue));
        String departmentAdvice = topDepartment
                .map(entry -> entry.getKey() + "入住人数最多，占比"
                        + String.format("%.1f%%", entry.getValue() * 100.0 / statistics.getTotalStudents()))
                .orElse("各系入住人数分布较均衡");

        String capacityAdvice;
        if (statistics.getOccupancyRate() >= 90) {
            capacityAdvice = "床位使用率偏高，建议提前统计下学期住宿需求，并预留应急床位。";
        } else if (statistics.getOccupancyRate() <= 60) {
            capacityAdvice = "当前仍有较多空余床位，可优先用于转专业、调宿或新生补录安排。";
        } else {
            capacityAdvice = "床位使用情况较平稳，可继续按现有规则进行日常分配。";
        }

        return statistics.getScopeValue()
                + "当前入住率为"
                + String.format("%.1f%%", statistics.getOccupancyRate())
                + "，空余床位"
                + statistics.getVacantBeds()
                + "个；"
                + departmentAdvice
                + "。"
                + capacityAdvice;
    }
}
