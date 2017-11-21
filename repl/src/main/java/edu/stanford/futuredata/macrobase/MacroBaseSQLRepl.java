package edu.stanford.futuredata.macrobase;

import org.antlr.v4.runtime.CharStreams;
import jline.console.ConsoleReader;
import jline.console.completer.*;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class MacroBaseSQLRepl {

    private static final String ASCII_ART_FILE = "ascii_art.txt";

    private static String readResourcesFile(final String filename) {

        StringBuilder result = new StringBuilder("");

        //Get file from resources folder

        File file = new File(MacroBaseSQLRepl.class.getClassLoader().getResource(filename).getFile());

        try (Scanner scanner = new Scanner(file)) {

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                result.append(line).append("\n");
            }

            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result.toString();
    }


    public static void parse(final String sql) {
        final List<String> functionNames = new ArrayList<>();


        // Create a lexer and parser for the input.
//        SQLiteLexer lexer = new SQLiteLexer(new ANTLRInputStream(sql));
        SqlBaseLexer lexer = new SqlBaseLexer(new UpperCaseCharStream(CharStreams.fromString(sql)));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ParseErrorListener);
        SqlBaseParser parser = new SqlBaseParser(new CommonTokenStream(lexer));

        // Invoke the `select_stmt` production.
        ParseTree tree = parser.select_stmt();
        SQLiteParser.Select_stmtContext ctx = parser.select_stmt();
        SQLiteParser.Select_or_valuesContext ctx2 = ctx.select_or_values(0);
        SQLiteParser.Table_or_subqueryContext ctx3 = ctx2.table_or_subquery(0);
        System.out.println("Table: " + ctx3.table_name().getText());

        // Walk the `select_stmt` production and listen when the parser
        // enters the `expr` production.
        ParseTreeWalker.DEFAULT.walk(new SQLiteBaseListener(){

            @Override
            public void enterExpr(@NotNull SQLiteParser.ExprContext ctx) {
                // Check if the expression is a function call.
                if (ctx.function_name() != null) {
                    // Yes, it was a function call: add the name of the function
                    // to out list.
                    functionNames.add(ctx.function_name().getText());
                }
            }
        }, tree);

        // Print the parsed functions.
        System.out.println("functionNames=" + functionNames);
    }


    private static void usage() {
        System.out.println("Usage: java " + MacroBaseSQLRepl.class.getName()
                + " [none/simple/files/dictionary [trigger mask]]");
        System.out.println("  none - no completors");
        System.out.println("  simple - a simple completor that comples "
                + "\"foo\", \"bar\", and \"baz\"");
        System.out
                .println("  files - a completor that comples " + "file names");
        System.out.println("  classes - a completor that comples "
                + "java class names");
        System.out
                .println("  trigger - a special word which causes it to assume "
                        + "the next line is a password");
        System.out.println("  mask - is the character to print in place of "
                + "the actual password character");
        System.out.println("  color - colored prompt and feedback");
        System.out.println("\n  E.g - java Example simple su '*'\n"
                + "will use the simple compleator with 'su' triggering\n"
                + "the use of '*' as a password mask.");
    }

    public static void main(String ...args) {
        // The list that will hold our function names.

        final String ascii_art = readResourcesFile(ASCII_ART_FILE);
        System.out.println(ascii_art);

        try {
            Character mask = null;
            String trigger = null;
            boolean color = false;

            ConsoleReader reader = new ConsoleReader();

            reader.setPrompt("macrodiff> ");

            if ((args == null) || (args.length == 0)) {
                usage();

                return;
            }

            List<Completer> completors = new LinkedList<>();

            switch (args[0]) {
                case "none":
                    break;
                case "files":
                    completors.add(new FileNameCompleter());
                    break;
                case "simple":
                    completors.add(new StringsCompleter("foo", "bar", "baz"));
                    break;
                case "color":
                    color = true;
                    reader.setPrompt("\u001B[32mmacrodiff\u001B[0m> ");
                    // completors.add(new AnsiStringsCompleter("\u001B[1mfoo\u001B[0m", "bar", "\u001B[32mbaz\u001B[0m"));
                    CandidateListCompletionHandler handler = new CandidateListCompletionHandler();
                    handler.setStripAnsi(true);
                    reader.setCompletionHandler(handler);
                    break;
                default:
                    usage();

                    return;
            }

            if (args.length == 3) {
                mask = args[2].charAt(0);
                trigger = args[1];
            }

            for (Completer c : completors) {
                reader.addCompleter(c);
            }

            String line;
            PrintWriter out = new PrintWriter(reader.getOutput());

            while ((line = reader.readLine()) != null) {
                if (color){
                    out.println("\u001B[33m======>\u001B[0m \"" + line + "\"");

                } else {
                    out.println("======> \"" + line + "\"");
                }
                out.flush();
                parse(line);

                // If we input the special word then we will mask
                // the next line.
                if ((trigger != null) && (line.compareTo(trigger) == 0)) {
                    line = reader.readLine("password> ", mask);
                }
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (line.equalsIgnoreCase("cls")) {
                    reader.clearScreen();
                }
            }
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
