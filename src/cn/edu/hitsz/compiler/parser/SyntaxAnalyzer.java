package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.*;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

// 实验二: 实现 LR 语法分析驱动程序

/**
 * LR 语法分析驱动程序
 * <br>
 * 该程序接受词法单元串与 LR 分析表 (action 和 goto 表), 按表对词法单元流进行分析, 执行对应动作, 并在执行动作时通知各注册的观察者.
 * <br>
 * 你应当按照被挖空的方法的文档实现对应方法, 你可以随意为该类添加你需要的私有成员对象, 但不应该再为此类添加公有接口, 也不应该改动未被挖空的方法,
 * 除非你已经同助教充分沟通, 并能证明你的修改的合理性, 且令助教确定可能被改动的评测方法. 随意修改该类的其它部分有可能导致自动评测出错而被扣分.
 */
public class SyntaxAnalyzer {
    private final SymbolTable symbolTable;
    private final List<ActionObserver> observers = new ArrayList<>();

    private Iterator<Token> tokens = null;
    private LRTable table = null;

    public SyntaxAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 注册新的观察者
     *
     * @param observer 观察者
     */
    public void registerObserver(ActionObserver observer) {
        observers.add(observer);
        observer.setSymbolTable(symbolTable);
    }

    /**
     * 在执行 shift 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param currentToken  当前词法单元
     */
    public void callWhenInShift(Status currentStatus, Token currentToken) {
        for (final var listener : observers) {
            listener.whenShift(currentStatus, currentToken);
        }
    }

    /**
     * 在执行 reduce 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param production    待规约的产生式
     */
    public void callWhenInReduce(Status currentStatus, Production production) {
        for (final var listener : observers) {
            listener.whenReduce(currentStatus, production);
        }
    }

    /**
     * 在执行 accept 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     */
    public void callWhenInAccept(Status currentStatus) {
        for (final var listener : observers) {
            listener.whenAccept(currentStatus);
        }
    }

    public void loadTokens(Iterable<Token> tokens) {
        // 加载词法单元
        // 你可以自行选择要如何存储词法单元, 譬如使用迭代器, 或是栈, 或是干脆使用一个 list 全存起来
        // 需要注意的是, 在实现驱动程序的过程中, 你会需要面对只读取一个 token 而不能消耗它的情况,
        // 在自行设计的时候请加以考虑此种情况

        this.tokens = tokens.iterator();
    }

    public void loadLRTable(LRTable table) {
        // 加载 LR 分析表
        // 你可以自行选择要如何使用该表格:
        // 是直接对 LRTable 调用 getAction/getGoto, 抑或是直接将 initStatus 存起来使用

        this.table = table;
    }

    public void run() {
        // 实现驱动程序
        // 你需要根据上面的输入来实现 LR 语法分析的驱动程序
        // 请分别在遇到 Shift, Reduce, Accept 的时候调用上面的 callWhenInShift, callWhenInReduce, callWhenInAccept
        // 否则用于为实验二打分的产生式输出可能不会正常工作

        // 符号栈
        Stack<Symbol> symbolStack = new Stack<>();
        // 状态栈
        Stack<Status> statusStack = new Stack<>();
        // 初始状态为(S0,eof)
        symbolStack.push(new Symbol(Token.eof()));
        statusStack.push(table.getInit());

        // 输入符号
        Token token = null;
        // 上一步是否为移位动作标志，初始化为true以读入第一个符号
        boolean isShift = true;
        while (tokens.hasNext() || !symbolStack.isEmpty()) {
            // 当前输入符号
            if(isShift){
                token = tokens.next();
                isShift = false;
            }
            // 根据栈顶元素和输入符号，得到对应动作
            Action action = table.getAction(statusStack.peek(),token);
            // 执行不同动作
            switch (action.getKind()){
                // 移进
                case Shift -> {
                    // 移进动作的状态
                    Status actionStatus = action.getStatus();
                    // 将输入符号与状态压入栈
                    symbolStack.push(new Symbol(token));
                    statusStack.push(actionStatus);
                    // 移进动作
                    callWhenInShift(actionStatus, token);
                    isShift = true;
                }
                // 归约
                case Reduce -> {
                    // 进行归约的产生式
                    Production production = action.getProduction();
                    // 将产生式右部的若干符号弹出
                    for(int i=0; i<production.body().size(); i++){
                        symbolStack.pop();
                        statusStack.pop();
                    }
                    // 再压入归约的产生式左部的符号
                    symbolStack.push(new Symbol(production.head()));
                    // 根据此时栈顶状态和归约得到的非终结符，得到将转移的状态并压入栈
                    statusStack.push(table.getGoto(statusStack.peek(), production.head()));
                    // 归约动作
                    callWhenInReduce(statusStack.peek(), production);
                }
                // 接受
                case Accept -> {
                    // 接受动作
                    callWhenInAccept(statusStack.peek());
                    return;
                }
                // 报错
                case Error -> {
                    return;
                }
            }
        }
    }

    class Symbol{
        Token token;
        NonTerminal nonTerminal;

        private Symbol(Token token, NonTerminal nonTerminal){
            this.token = token;
            this.nonTerminal = nonTerminal;
        }

        public Symbol(Token token){
            this(token, null);
        }

        public Symbol(NonTerminal nonTerminal){
            this(null, nonTerminal);
        }

        public boolean isToken(){
            return this.token != null;
        }

        public boolean isNonterminal(){
            return this.nonTerminal != null;
        }
    }

}
