package cn.nukkit.entity;

import java.util.UUID;

/**
 * 属性修改器类，用于管理不同来源的属性修改
 * Attribute modifier class for managing attribute modifications from different sources
 */
public class AttributeModifier {
    
    /**
     * 修改器操作类型枚举
     * Modifier operation type enumeration
     */
    public enum Operation {
        /**
         * 加法操作：直接加到基础值上
         * Addition operation: directly add to base value
         */
        ADDITION(0),
        
        /**
         * 基础乘法：(基础值 + 所有加法修改器) * (1 + 所有基础乘法修改器)
         * Base multiplication: (base + all additions) * (1 + all base multipliers)
         */
        MULTIPLY_BASE(1),
        
        /**
         * 总乘法：前面的结果 * (1 + 乘法修改器)
         * Total multiplication: previous result * (1 + multiplier)
         */
        MULTIPLY_TOTAL(2),
        
        /**
         * 上限操作：限制最大值
         * Cap operation: limit maximum value
         */
        CAP(3);
        
        private final int id;
        
        Operation(int id) {
            this.id = id;
        }
        
        public int getId() {
            return id;
        }
        
        public static Operation fromId(int id) {
            for (Operation op : values()) {
                if (op.id == id) {
                    return op;
                }
            }
            return ADDITION;
        }
    }
    
    private final UUID id;
    private final String name;
    private final double amount;
    private final Operation operation;
    private final int operand;
    
    /**
     * 构造函数
     * @param id 修改器唯一标识
     * @param name 修改器名称
     * @param amount 修改数值
     * @param operation 操作类型
     */
    public AttributeModifier(UUID id, String name, double amount, Operation operation) {
        this(id, name, amount, operation, 0);
    }
    
    /**
     * 构造函数
     * @param id 修改器唯一标识
     * @param name 修改器名称
     * @param amount 修改数值
     * @param operation 操作类型
     * @param operand 操作数
     */
    public AttributeModifier(UUID id, String name, double amount, Operation operation, int operand) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.operation = operation;
        this.operand = operand;
    }
    
    /**
     * 便捷构造函数，自动生成UUID
     * @param name 修改器名称
     * @param amount 修改数值
     * @param operation 操作类型
     */
    public AttributeModifier(String name, double amount, Operation operation) {
        this(UUID.randomUUID(), name, amount, operation);
    }
    
    public UUID getUuid() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public Operation getOperation() {
        return operation;
    }
    
    public int getOperand() {
        return operand;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AttributeModifier that = (AttributeModifier) obj;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return "AttributeModifier{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", amount=" + amount +
                ", operation=" + operation +
                ", operand=" + operand +
                '}';
    }
}