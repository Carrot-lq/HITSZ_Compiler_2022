package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.text.StringCharacterIterator;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    // 符号表
    private final SymbolTable symbolTable;
    // 词法单元列表
    private final List<Token> tokens = new LinkedList<>();

    // 字符串字符迭代器
    private StringCharacterIterator iterator = null;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法

        // 直接读入文本文件中所有字符并创建StringCharacterIterator
        iterator = new StringCharacterIterator(FileUtils.readFile(path));
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // 自动机实现的词法分析过程

        // 当前状态
        int currentState = 0;
        // 接受状态集合
        Set<Integer> acceptedStates = new HashSet<>(Arrays.asList(2,4,5,6,7,8,9,10,11,12,13));
        // 关键词集合
        Set<String> keyWords = new HashSet<>(Arrays.asList("int","return"));

        StringBuilder id = new StringBuilder();
        StringBuilder intConst = new StringBuilder();

        // 外循环：未读完所有字符
        while (iterator.current() != StringCharacterIterator.DONE) {
            // 内循环：当前已读取字符未转移至接受状态且未读至结尾
            while (!acceptedStates.contains(currentState)
                    && iterator.current() != StringCharacterIterator.DONE) {
                // 读取当前字符
                char c = iterator.current();
                // 指导书建议尽可能使用 Character 中的方法，而非直接判断 ASCII 编码大小
                // 但查阅相关资料发现，Character.isDigit有可能把ASCII以外的某些字符当做是数字
                // 数字判断的严格性，从严到松依次是：
                // c >= '0' && c <= '9' ⇒IsDigit ⇒IsNumber
                boolean isDigit = ('0' <= c && c <= '9');
                boolean isLetter = ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
                boolean isUnderline = (c == '_');
                // 状态转移的下一个状态
                int nextState = switch (currentState) {
                    case 0 -> switch (c) {
                        case ' ','\t','\n','\r' -> 0;
                        case '=' -> 5;
                        case ',' -> 6;
                        case ';' -> 7;
                        case '+' -> 8;
                        case '-' -> 9;
                        case '*' -> 10;
                        case '/' -> 11;
                        case '(' -> 12;
                        case ')' -> 13;
                        default -> {
                            if (isLetter || isUnderline) yield 1;
                            if (isDigit) yield 3;
                            yield -1;
                        }
                    };
                    // 读取完字符与数字后需回退一个字符
                    case 1 -> {
                        if(isLetter) yield 1;
                        else {
                            iterator.previous();
                            yield 2;
                        }
                    }
                    case 3 -> {
                        if(isDigit) yield 3;
                        else {
                            iterator.previous();
                            yield 4;
                        }
                    }
                    default -> acceptedStates.contains(currentState) ? 0 : -1;
                };

                // 暂存正在读取的letter与数字的字符
                switch (currentState) {
                    case 0:
                        if (isLetter) id.append(c);
                        if (isDigit) intConst.append(c);
                        break;
                    case 1:
                        if (nextState == 1) id.append(c);
                        break;
                    case 3:
                        if (nextState == 3) intConst.append(c);
                    default:
                        break;
                }

                // 若将进入接受状态，填入词法单元与符号表
                if (acceptedStates.contains(nextState)) {
                    tokens.add(switch (nextState) {
                        // 接受标识符结束
                        case 2 -> {
                            // 提取标识符并清空StringBuilder
                            String str = id.toString();
                            id.setLength(0);
                            // 省略关键词，填入符号表
                            if (!keyWords.contains(str) && !symbolTable.has(str)) {
                                symbolTable.add(str);
                            }
                            // 关键词与标识符填入词法单元表
                            yield keyWords.contains(str) ? Token.simple(str) : Token.normal("id", str);
                        }
                        // 接受整型数结束
                        case 4 -> {
                            // 提取整型数并清空StringBuilder
                            String str = intConst.toString();
                            intConst.setLength(0);
                            // 整型数填入此法单元表
                            yield Token.normal("IntConst", str);
                        }
                        // 接受其他符号
                        case 5 -> Token.simple("=");
                        case 6 -> Token.simple(",");
                        case 7 -> Token.simple("Semicolon");
                        case 8 -> Token.simple("+");
                        case 9 -> Token.simple("-");
                        case 10 -> Token.simple("*");
                        case 11 -> Token.simple("/");
                        case 12 -> Token.simple("(");
                        case 13 -> Token.simple(")");
                        default -> throw new RuntimeException("Illegal State!");
                    });
                }
                // 状态转移
                iterator.next();
                currentState = nextState;
            }
            // 读完进入接受状态后，初始化当前状态
            currentState = 0;
        }
        //末尾添加EOF
        tokens.add(Token.eof());
        System.out.println("Lexical analyze over");
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }


}
