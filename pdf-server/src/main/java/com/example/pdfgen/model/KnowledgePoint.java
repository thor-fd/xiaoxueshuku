package com.example.pdfgen.model;

/** 口算知识点：既有课标难度分级，也有「运算 × 位数」自由组合（H5/小程序选数学时直接选运算和位数） */
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
    MUL99("表内乘法"),
    /** 一位数加法 */
    ADD1("一位数加法"),
    /** 两位数加法 */
    ADD2("两位数加法"),
    /** 一位数减法 */
    SUB1("一位数减法"),
    /** 两位数减法 */
    SUB2("两位数减法"),
    /** 一位数乘法（表内口诀） */
    MUL1("一位数乘法"),
    /** 两位数乘法（两位数 × 一位数） */
    MUL2("两位数乘法"),
    /** 一位数除法（被除数一位数，整除） */
    DIV1("一位数除法"),
    /** 两位数除法（被除数两位数 ÷ 一位数，整除） */
    DIV2("两位数除法");

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
