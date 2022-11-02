package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

// 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {

    private SymbolTable symbolTable = null;

    // 语义分析栈
    private final Stack<Symbol> symbolStack = new Stack<>();
    private final Stack<IRValue> irValueStack = new Stack<>();
    // 生成代码列表
    private final List<Instruction> instList = new LinkedList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        symbolStack.push(new Symbol(currentToken));
        irValueStack.push(null);
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // 根据归约的产生式进行不同操作
        switch (production.index()) {
            case 6 -> { // S -> id = E;
                symbolStack.pop();
                symbolStack.pop();
                Symbol id = symbolStack.pop();
                IRValue EValue = irValueStack.pop();
                irValueStack.pop();
                irValueStack.pop();

                // id为具体变量
                String text = id.token.getText();
                if(!symbolTable.has(text)){
                    throw new RuntimeException("SymbolTable no such id");
                }
                IRVariable idValue = IRVariable.named(text);
                // MOV id E
                instList.add(Instruction.createMov(idValue, EValue));

                // 压入S value为null
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(null);
            }
            case 7 -> { // S -> return E;
                symbolStack.pop();
                symbolStack.pop();
                IRValue EValue = irValueStack.pop();
                irValueStack.pop();

                // RET E
                instList.add(Instruction.createRet(EValue));

                // 压入S value为null
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(null);
            }
            case 8 -> { // E -> E + A
                symbolStack.pop();
                symbolStack.pop();
                symbolStack.pop();
                IRValue AValue = irValueStack.pop();
                irValueStack.pop();
                IRValue EValue = irValueStack.pop();

                // 计算结果为临时变量
                IRVariable temp = IRVariable.temp();
                // ADD E A
                instList.add(Instruction.createAdd(temp, EValue, AValue));

                // 压入E value为temp
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(temp);
            }
            case 9 -> { // E -> E - A;
                symbolStack.pop();
                symbolStack.pop();
                symbolStack.pop();
                IRValue AValue = irValueStack.pop();
                irValueStack.pop();
                IRValue EValue = irValueStack.pop();

                // 计算结果为临时变量
                IRVariable temp = IRVariable.temp();
                // SUB E A
                instList.add(Instruction.createSub(temp, EValue, AValue));

                // 压入E value为temp
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(temp);
            }
            case 10 -> { // E -> A;
                symbolStack.pop();
                IRValue AValue = irValueStack.pop();

                // 压入E value为A的value
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(AValue);
            }
            case 11 -> { // A -> A * B;
                symbolStack.pop();
                symbolStack.pop();
                symbolStack.pop();
                IRValue BValue = irValueStack.pop();
                irValueStack.pop();
                IRValue AValue = irValueStack.pop();

                // 计算结果为临时变量
                IRVariable temp = IRVariable.temp();
                // MUL A B
                instList.add(Instruction.createMul(temp, AValue, BValue));

                // 压回E
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(temp);
            }

            case 12 -> { // A -> B;
                symbolStack.pop();
                IRValue BValue = irValueStack.pop();

                // 压入A value为B的value
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(BValue);
            }
            case 13 -> { // B -> ( E );
                symbolStack.pop();
                symbolStack.pop();
                symbolStack.pop();
                irValueStack.pop();
                IRValue EValue = irValueStack.pop();
                irValueStack.pop();

                // 压入B value为E的value
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(EValue);
            }
            case 14 -> { // B -> id;
                Symbol id = symbolStack.pop();
                irValueStack.pop();

                // B为具体变量
                String text = id.token.getText();
                if(!symbolTable.has(text)){
                    throw new RuntimeException("SymbolTable no such id");
                }
                IRVariable BValue = IRVariable.named(text);

                // 压入B value为named具体变量
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(BValue);
            }
            case 15 -> { // B -> IntConst;
                Symbol IntConst = symbolStack.pop();
                irValueStack.pop();


                // B为立即数
                String text = IntConst.token.getText();
                IRImmediate BValue = IRImmediate.of(Integer.parseInt(text));

                // 压入B value为immediate立即数
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(BValue);
            }
            default -> {
                for (int i = 1; i < production.body().size(); i++) {
                    symbolStack.pop();
                    irValueStack.pop();
                }

                // 压入其他产生式头 value为空
                //symbolStack.push(new Symbol(production.head()));
                irValueStack.push(null);
            }
        }
        symbolStack.push(new Symbol(production.head()));
    }


    @Override
    public void whenAccept(Status currentStatus) {

    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }

    public List<Instruction> getIR() {
        return instList;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}

