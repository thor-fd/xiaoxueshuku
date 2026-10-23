package com.example.pdfgen.model;

/** 口算知识点，对应课标难度分级 */
public enum KnowledgePoint {
    /** 10以内加法 */
    ADD10("10以内加法"),
    /** 10以内减法 */
    SUB10("10以内减法"),
    /** 20以内进位加法 */
    ADD20CARRY("20以内进位加法"),
    /** 20以内退位减法 */
    SUB20BORROW("20以内退位减法"),
    /** 表内乘法 */
    MUL99("表内乘法");

    public final String label;

    KnowledgePoint(String label) {
        this.label = label;
    }

    /** 按中文名或枚举名解析（如「20以内进位加法」或「ADD20CARRY」） */
    public static KnowledgePoint byLabel(String label) {
        for (KnowledgePoint kp : values()) {
            if (kp.label.equals(label) || kp.name().equalsIgnoreCase(label)) {
                return kp;
            }
        }
        throw new IllegalArgumentException("未知知识点: " + label);
    }
}
