package com.wtm.employee;

/** One duty and the qualification required to perform it. */
public record DutyRequirement(
        String duty,
        String qualification,
        int requiredCount
) {
    public DutyRequirement {
        duty=duty==null?"":duty.trim();
        qualification=qualification==null?"":qualification.trim();
        requiredCount=Math.max(1,requiredCount);
    }
}
