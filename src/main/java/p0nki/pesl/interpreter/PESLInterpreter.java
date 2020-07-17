package p0nki.pesl.interpreter;

import org.apache.commons.cli.*;
import p0nki.pesl.api.PESLContext;
import p0nki.pesl.api.PESLEvalException;
import p0nki.pesl.api.object.FunctionObject;
import p0nki.pesl.api.object.PESLObject;
import p0nki.pesl.api.object.UndefinedObject;
import p0nki.pesl.api.parse.PESLParseException;
import p0nki.pesl.api.parse.PESLParser;
import p0nki.pesl.api.token.PESLTokenList;
import p0nki.pesl.api.token.PESLTokenizeException;
import p0nki.pesl.api.token.PESLTokenizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PESLInterpreter {

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder("h").longOpt("help").desc("shows this help command").numberOfArgs(0).build());
        options.addOption(Option.builder("f").longOpt("files").desc("source files for interpreter").hasArgs().build());
        options.addOption(Option.builder("r").longOpt("repl").desc("starts a PESL repl").numberOfArgs(0).build());
        options.addOption(Option.builder("i").longOpt("info").desc("information about this interpreter").numberOfArgs(0).build());

        CommandLineParser commandParser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();
        CommandLine cmd = null;
        try {
            cmd = commandParser.parse(options, args);
        } catch (ParseException parseException) {
            System.out.println(parseException.getMessage());
            helpFormatter.printHelp("java -jar pesl.jar", options);
            System.exit(1);
        }

        if (cmd.hasOption("help")) {
            helpFormatter.printHelp("java -jar pesl.jar", options);
            return;
        }

        if (cmd.hasOption("info")) {
            System.out.println("This is a command-line interpreter and REPL for PESL, P0nki's Epic Scripting Language.");
            System.out.println("Source code for PESL can be found here: https://github.com/jearmstrong21/PESL");
            System.out.println("Run with -h or --help for arguments help.");
            System.out.println();
            System.out.println("The context ran in this interpreter is slightly different from the default one.");
            System.out.println("You are given println([arg]), a function which takes an optional argument and directly prints it to stdout, with a newline.");
            System.out.println("You are also given exit([code]), a function which takes an optional exit code.");
            return;
        }

        boolean files = cmd.hasOption("files");
        boolean repl = cmd.hasOption("repl");
        if (files && repl) {
            System.err.println("Cannot run files and repl");
            System.exit(1);
        }

        if (!files && !repl) {
            repl = true;
        }

        PESLContext context = new PESLContext();
        context.setKey("println", FunctionObject.of(false, arguments -> {
            PESLEvalException.validArgumentListLength(arguments, 0, 1);
            if (arguments.size() == 0) System.out.println();
            else System.out.println(arguments.get(0).stringify());
            return UndefinedObject.INSTANCE;
        }));
        context.setKey("exit", FunctionObject.of(false, arguments -> {
            PESLEvalException.validArgumentListLength(arguments, 0, 1);
            if (arguments.size() == 0) System.exit(0);
            else System.exit((int) arguments.get(0).asNumber().getValue());
            return UndefinedObject.INSTANCE;
        }));

        PESLTokenizer tokenizer = new PESLTokenizer();
        PESLParser parser = new PESLParser();

        if (files) {
            String[] values = cmd.getOptionValues("files");
            List<String> codeFiles = new ArrayList<>();
            for (String s : values) {
                File f = new File(s);
                if (f.exists() && f.isFile() && f.getName().endsWith(".pesl") && f.getName().length() > 5) {
                    try {
                        codeFiles.add(Files.readString(Path.of(f.toURI())));
                    } catch (IOException ioException) {
                        System.err.println("Error reading file " + s);
                        System.exit(1);
                    }
                } else {
                    System.err.println(s + " is not a file");
                    System.exit(1);
                }
            }
            String overallCode = String.join(" ", codeFiles);
            PESLTokenList tokens = null;
            try {
                tokens = tokenizer.tokenize(overallCode);
            } catch (PESLTokenizeException e) {
                System.err.println(e.getMessage());
                System.err.println("At index " + e.getIndex());
                System.exit(1);
            }
            while (tokens.hasAny()) {
                try {
                    parser.parseExpression(tokens).evaluate(context);
                } catch (PESLParseException e) {
                    System.err.println(e.getMessage());
                    System.err.println("At token " + e.getToken() + " [" + e.getToken().getStart() + ", " + e.getToken().getEnd() + "]");
                    System.exit(1);
                } catch (PESLEvalException e) {
                    System.err.println(e.getObject().stringify());
                    System.exit(1);
                }
            }
        }

        if (repl) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("PESL ");
            //noinspection InfiniteLoopStatement
            while (true) {
                System.out.print(">>> ");
                String code = scanner.nextLine();
                PESLTokenList tokens;
                try {
                    tokens = tokenizer.tokenize(code);
                } catch (PESLTokenizeException e) {
                    System.err.println(e.getMessage());
                    System.err.println("At index " + e.getIndex());
                    continue;
                }
                while (tokens.hasAny()) {
                    try {
                        PESLObject object = parser.parseExpression(tokens).evaluate(context);
                        System.out.println(object.stringify());
                    } catch (PESLParseException e) {
                        System.err.println(e.getMessage());
                        System.err.println("At token " + e.getToken() + " [" + e.getToken().getStart() + ", " + e.getToken().getEnd() + "]");
                        break;
                    } catch (PESLEvalException e) {
                        System.err.println(e.getObject().stringify());
                        break;
                    }
                }
            }
        }
    }

}
