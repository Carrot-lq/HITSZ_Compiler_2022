package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.Stack;

// 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {

    private SymbolTable symbolTable = null;

    // 语义分析栈
    Stack<Symbol> symbolStack = new Stack<>();
    Stack<SourceCodeType> typeStack = new Stack<>();

    @Override
    public void whenAccept(Status currentStatus) {
        //  该过程在遇到 Accept 时要采取的代码动作

    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // 该过程在遇到 reduce production 时要采取的代码动作
        switch (production.index()) {
            // 我们推荐在 case 后面使用注释标明产生式
            // 这样能比较清楚地看出产生式索引与产生式的对应关系
            case 4 -> { // S -> D id
                // 查找id并修改其type为D对应type
                Symbol id = symbolStack.pop();
                Symbol D = symbolStack.pop();
                SourceCodeType idType = typeStack.pop();
                SourceCodeType DType = typeStack.pop();

                String text = id.token.getText();
                if(symbolTable.has(text)){
                    symbolTable.get(text).setType(DType);
                } else {
                    throw new RuntimeException("SymbolTable no such id");
                }
                // 压入S type为空
                //symbolStack.push(new Symbol(production.head()));
                typeStack.push(null);
            }
            case 5 -> { // D -> int
                symbolStack.pop();
                typeStack.pop();
                // 压入D type为int
                //symbolStack.push(new Symbol(production.head()));
                typeStack.push(SourceCodeType.Int);
            }
            default -> {
                if(production.index() < 1 || production.index() > 15){
                    throw new RuntimeException("Illegal production index");
                }
                for (int i = 1; i < production.body().size(); i++) {
                    symbolStack.pop();
                    typeStack.pop();
                }
                // 压入其他产生式头 type为空
                //symbolStack.push(new Symbol(production.head()));
                typeStack.push(null);
            }
        }
        symbolStack.push(new Symbol(production.head()));
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // 该过程在遇到 shift 时要采取的代码动作

        // 将token入栈，type初始为空
        symbolStack.push(new Symbol(currentToken));
        typeStack.push(null);
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储

        this.symbolTable = table;
    }
}

