package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.*;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {

    // 预处理后指令列表
    List<Instruction> instList = new LinkedList<>();
    // 变量与寄存器双向map
    BMap<IRValue, Register> variableMap = new BMap<>();
    // 汇编代码，初始化第一行为 ".text"
    List<String> sentences = new ArrayList<>(List.of(".text"));
    // 可以分配的寄存器号
    enum Register {
        t0,t1,t2,t3,t4,t5,t6
    }

    public void addVariable(IRValue operand, int instIndex){
        // 立即数无需分配寄存器
        if (operand.isImmediate()) {
            return;
        }
        // 已存入变量，无需再分配
        if (variableMap.containsKey(operand)) {
            return;
        }
        // 未存入，先寻找空闲寄存器
        for (Register reg : Register.values()) {
            if(!variableMap.containsValue(reg)){
                variableMap.replace(operand,reg);
                return;
            }
        }
        // 若均不空闲，寻找后续不再使用的变量占用的寄存器
        Set<Register> cleanableRegs = Arrays.stream(Register.values()).collect(Collectors.toSet());
        for(int i = instIndex; i<instList.size(); i++){
            // 排除后续每一条指令所有出现的变量所占用的寄存器（若存在），剩余即为可被清理的寄存器
            Instruction inst = instList.get(i);
            for(IRValue irValue : inst.getOperands()){
                Register reg = variableMap.getByKey(irValue);
                cleanableRegs.remove(reg);
            }
        }
        // 存在可清理寄存器则将其分配
        if(!cleanableRegs.isEmpty()) {
            variableMap.replace(operand,cleanableRegs.iterator().next());
        }
    }

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        for (Instruction inst : originInstructions) {
            switch (inst.getKind()) {
                // 两个操作数的指令
                case ADD,SUB,MUL -> {
                    // 预处理操作数有立即数的指令
                    boolean lhsIsImm = inst.getLHS().isImmediate();
                    boolean rhsIsImm = inst.getRHS().isImmediate();
                    if (lhsIsImm && rhsIsImm) {
                        // 两个立即数直接求值得到结果
                        InstructionKind kind = inst.getKind();
                        int l = ((IRImmediate)inst.getLHS()).getValue();
                        int r = ((IRImmediate)inst.getRHS()).getValue();
                        int res = switch (kind) {
                            case ADD -> l + r;
                            case SUB -> l - r;
                            case MUL -> l * r;
                            default -> 0;
                        };
                        IRImmediate tmp = IRImmediate.of(res);
                        instList.add(Instruction.createMov(inst.getResult(),tmp));
                    } else if (lhsIsImm && !rhsIsImm) {
                        // 左立即数修改指令
                        switch (inst.getKind()) {
                            // 加法交换左立即数至右边
                            case ADD -> instList.add(Instruction.createAdd(inst.getResult(),inst.getRHS(),inst.getLHS()));
                            // 减法与乘法指令前添加 MOV tmp imm
                            case SUB -> {
                                IRVariable tmp = IRVariable.temp();
                                instList.add(Instruction.createMov(tmp,inst.getLHS()));
                                instList.add(Instruction.createSub(inst.getResult(),tmp,inst.getRHS()));
                            }
                            case MUL -> {
                                IRVariable tmp = IRVariable.temp();
                                instList.add(Instruction.createMov(tmp,inst.getLHS()));
                                instList.add(Instruction.createMul(inst.getResult(),tmp,inst.getLHS()));
                            }
                            default -> instList.add(inst);
                        }
                    } else if (!lhsIsImm && rhsIsImm) {
                        // 右立即数修改指令
                        // 乘法指令前添加 MOV tmp IMM
                        if (inst.getKind() == InstructionKind.MUL) {
                            IRVariable tmp = IRVariable.temp();
                            instList.add(Instruction.createMov(tmp,inst.getLHS()));
                            instList.add(Instruction.createMul(inst.getResult(),tmp,inst.getLHS()));
                        } else {
                            instList.add(inst);
                        }
                    } else {
                        // 两个操作数均不为立即数，无需处理
                        instList.add(inst);
                    }
                }
                // 一个操作数的指令
                case RET -> {
                    // 遇到RET指令后直接舍弃后续指令
                    instList.add(inst);
                    return;
                }
                case MOV -> instList.add(inst);
            }
        }
    }

    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // 执行寄存器分配与代码生成
        for(int i=0;i<instList.size();i++) {
            Instruction inst = instList.get(i);
            String str = null;
            switch (inst.getKind()) {
                // 对ADD SUB MUL，为三个变量分配寄存器（若需要），并根据是否含立即数生成对应汇编代码
                case ADD -> {

                    IRValue res = inst.getResult();
                    IRValue lhs = inst.getLHS();
                    IRValue rhs = inst.getRHS();
                    addVariable(res,i);
                    addVariable(lhs,i);
                    addVariable(rhs,i);
                    Register resReg = variableMap.getByKey(res);
                    Register lhsReg = variableMap.getByKey(lhs);
                    Register rhsReg = variableMap.getByKey(rhs);
                    if(rhs.isImmediate()){
                         str = "\taddi %s,%s,%s".formatted(resReg.toString(),lhsReg.toString(),rhs.toString());
                    } else {
                        str = "\tadd %s,%s,%s".formatted(resReg.toString(),lhsReg.toString(),rhsReg.toString());
                    }
                }
                case SUB -> {
                    IRValue res = inst.getResult();
                    IRValue lhs = inst.getLHS();
                    IRValue rhs = inst.getRHS();
                    addVariable(res,i);
                    addVariable(lhs,i);
                    addVariable(rhs,i);
                    Register resReg = variableMap.getByKey(res);
                    Register lhsReg = variableMap.getByKey(lhs);
                    Register rhsReg = variableMap.getByKey(rhs);
                    if(rhs.isImmediate()){
                        str = "\tsubi %s,%s,%s".formatted(resReg.toString(),lhsReg.toString(),rhs.toString());
                    } else {
                        str = "\tsub %s,%s,%s".formatted(resReg.toString(),lhsReg.toString(),rhsReg.toString());
                    }
                }
                case MUL -> {
                    IRValue res = inst.getResult();
                    IRValue lhs = inst.getLHS();
                    IRValue rhs = inst.getRHS();
                    addVariable(res,i);
                    addVariable(lhs,i);
                    addVariable(rhs,i);
                    Register resReg = variableMap.getByKey(res);
                    Register lhsReg = variableMap.getByKey(lhs);
                    Register rhsReg = variableMap.getByKey(rhs);
                    str = "\tmul %s,%s,%s".formatted(resReg.toString(),lhsReg.toString(),rhsReg.toString());
                }
                // 对MOV，为两个变量分配寄存器（若需要）
                // 若第二个操作数为立即数，生成汇编代码为 li（加载立即数），否则为 mv
                case MOV -> {
                    IRValue res = inst.getResult();
                    IRValue from = inst.getFrom();
                    addVariable(res,i);
                    addVariable(from,i);
                    Register resReg = variableMap.getByKey(res);
                    Register fromReg = variableMap.getByKey(from);
                    if (from.isImmediate()) {
                        str = "\tli %s,%s".formatted(resReg.toString(),from.toString());
                    } else {
                        str = "\tmv %s,%s".formatted(resReg.toString(),fromReg.toString());
                    }
                }
                // 对RET，生成汇编代码为 mv a0 __
                case RET -> str = "\tmv a0," + variableMap.getByKey(inst.getReturnValue()).toString();
            }
            // 添加注释，即对应中间代码
            str = str + "\t# %s".formatted(inst.toString());
            sentences.add(str);
            // 读取到RET指令后，直接舍弃后续指令
            if (inst.getKind() == InstructionKind.RET) {
                break;
            }
        }
        System.out.println("Assembly Generate over");
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        // 输出汇编代码到文件
        FileUtils.writeLines(path,sentences.stream().toList());
    }
}

