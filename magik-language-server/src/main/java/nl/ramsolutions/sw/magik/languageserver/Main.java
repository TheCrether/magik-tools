package nl.ramsolutions.sw.magik.languageserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.LogManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/** Main entry point. */
public final class Main {

  private static final Options OPTIONS;
  private static final Option OPTION_DEBUG =
      Option.builder().longOpt("debug").desc("Show debug messages").build();
  private static final Option OPTION_STDIO =
      Option.builder()
          .longOpt("stdio")
          .desc("Use STDIO (default, no other option to interface with this language server)")
          .build();

  static {
    OPTIONS = new Options();
    OPTIONS.addOption(OPTION_DEBUG);
    OPTIONS.addOption(OPTION_STDIO);
  }

  private Main() {}

  /**
   * Initialize logger from logging.properties.
   *
   * @throws IOException -
   */
  private static void initLogger() throws IOException {
    final InputStream stream =
        Main.class.getClassLoader().getResourceAsStream("logging.properties");
    LogManager.getLogManager().readConfiguration(stream); // NOSONAR: Own logging configuration.
  }

  /**
   * Initialize logger from debug-logging.properties.
   *
   * @throws IOException -
   */
  private static void initDebugLogger() throws IOException {
    final InputStream stream =
        Main.class.getClassLoader().getResourceAsStream("debug-logging.properties");
    LogManager.getLogManager().readConfiguration(stream); // NOSONAR: Own logging configuration.
  }

  /**
   * Parse the command line.
   *
   * @param args Command line arguments.
   * @return Parsed command line.
   * @throws ParseException -
   */
  private static CommandLine parseCommandline(final String[] args) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    return parser.parse(Main.OPTIONS, args);
  }

  /**
   * Main entry point.
   *
   * @param args Arguments.
   * @throws IOException -
   * @throws ParseException -
   */
  public static void main(final String[] args) throws IOException, ParseException {
    final CommandLine commandLine = Main.parseCommandline(args);
    if (commandLine.hasOption(OPTION_DEBUG)) {
      Main.initDebugLogger();
    } else {
      Main.initLogger();
    }

    final MagikLanguageServer server = new MagikLanguageServer();
    Launcher<LanguageClient> launcher;
    if (commandLine.hasOption(OPTION_DEBUG)) {
      Function<MessageConsumer, MessageConsumer> wrapper = consumer -> (MessageConsumer) consumer;
      launcher =
          createSocketLauncher(
              server,
              new InetSocketAddress("localhost", 5007),
              Executors.newCachedThreadPool(),
              wrapper);
    } else {
      launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
    }

    assert launcher != null;
    final LanguageClient remoteProxy = launcher.getRemoteProxy();
    server.connect(remoteProxy);

    launcher.startListening();
  }

  static <T> Launcher<T> createSocketLauncher(
      Object localService,
      SocketAddress socketAddress,
      ExecutorService executorService,
      Function<MessageConsumer, MessageConsumer> wrapper)
      throws IOException {
    AsynchronousServerSocketChannel serverSocket =
        AsynchronousServerSocketChannel.open().bind(socketAddress);
    AsynchronousSocketChannel socketChannel;
    try {
      socketChannel = serverSocket.accept().get();
      return Launcher.createIoLauncher(
          localService,
          (Class<T>) LanguageClient.class,
          Channels.newInputStream(socketChannel),
          Channels.newOutputStream(socketChannel),
          executorService,
          wrapper);
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  }
}
