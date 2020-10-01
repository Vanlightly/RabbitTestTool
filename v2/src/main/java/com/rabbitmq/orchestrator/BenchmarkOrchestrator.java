package com.rabbitmq.orchestrator;

import com.rabbitmq.orchestrator.model.Playlist;
import com.rabbitmq.orchestrator.model.Benchmark;
import com.rabbitmq.orchestrator.parsers.PlaylistParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;

public class BenchmarkOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger("MAIN");

    public static void main(String[] args) {
        CmdArguments arguments = new CmdArguments(args);
        if(arguments.hasRequestedHelp()) {
            CmdArguments.printHelp(System.out);
            System.exit(0);
        }

        BenchmarkOrchestrator orchestrator = new BenchmarkOrchestrator();

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                System.out.println("Shutting down deployer and runner");
                orchestrator.shutdown();
                System.out.println("Shutdown complete");
            }
        });

        orchestrator.run(arguments);
    }

    WorkDispatcher dispatcher;

    public void run(CmdArguments arguments) {
        String playlistFile = arguments.getStr("--playlist-file");
        boolean noDeploy = arguments.getBoolean("--no-deploy", false);
        boolean noDestroy = arguments.getBoolean("--no-destroy", false);
        String tags = arguments.getStr("--tags", "none");
        String runTag = String.valueOf(new Random().nextInt(999999));

        if(noDeploy)
            runTag = arguments.getStr("--run-tag");

        // step 1 - parse yaml
        String metaDataDir = arguments.getStr("--meta-data-dir");
        String configDir = arguments.getStr("--config-dir");
        PlaylistParser playlistParser = new PlaylistParser(metaDataDir, configDir);
        playlistParser.loadPlaylist(playlistFile);

        Playlist playlist = playlistParser.getPlaylist();
        ProcessExecutor processExecutor = new ProcessExecutor();
        dispatcher = new WorkDispatcher(playlistParser,
                playlistParser.loadOutputData(),
                processExecutor,
                noDeploy,
                noDestroy);

        boolean deploySuccess = dispatcher.deploySystems(runTag, playlist.getSystems());
        if(!deploySuccess) {
            LOGGER.warn("Deployment failed, terminating run");
            System.exit(1);
        }

        // step 3 - run benchmarks
        String runId = UUID.randomUUID().toString();
        for(Benchmark benchmark : playlist.getBenchmarks()) {
            boolean updateSuccess = dispatcher.updateBrokers(benchmark);
            if(!updateSuccess){
                LOGGER.warn("Terminating playlist run due to failure to update the broker configuration");
                break;
            }

            boolean runSuccess = dispatcher.run(benchmark, tags, runId);
            if(!runSuccess) {
                LOGGER.warn("Terminating playlist run due to failure of the previous benchmark");
                break;
            }

            dispatcher.restartBrokers();
        }

        dispatcher.retrieveLogs();
        dispatcher.teardown();
    }

    public void shutdown() {
        if(dispatcher != null)
            dispatcher.cancelAllOperations();
    }


}