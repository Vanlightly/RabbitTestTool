package com.rabbitmq.orchestrator.deploy.k8s;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.ProcessExecutor;
import com.rabbitmq.orchestrator.deploy.RabbitConfigGenerator;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sSystem;
import freemarker.core.ParseException;
import freemarker.template.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class ManifestGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("ManifestGenerator");

    Configuration cfg;

    public ManifestGenerator(File manifestDir) throws IOException {
        cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setDirectoryForTemplateLoading(manifestDir);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
    }

    public String generateManifest(K8sSystem system, ProcessExecutor processExecutor) {
        try {
            Template temp = cfg.getTemplate("cluster-template.yaml");

            Map<String, Object> root = new HashMap<>();
            root.put("cluster_name", system.getRabbitClusterName());
            root.put("cluster_size", system.getHardware().getInstanceCount());
            root.put("image", system.getRabbitmq().getRabbitmqImage());
            root.put("volume_size", toStr(system.getHardware().getVolumeConfig().getSizeGb()));
            root.put("volume_type", String.valueOf(system.getHardware().getVolumeConfig().getVolumeType()).toLowerCase());
            root.put("cpu_limit", system.getHardware().getCpuLimit());
            root.put("memory_mb_limit", toStr(system.getHardware().getMemoryMbLimit()));
            root.put("standard_config", RabbitConfigGenerator.generateStandardConfigList(system.getRabbitmqConfig().getStandard()));
            root.put("advanced_config_rabbit", RabbitConfigGenerator.generateAdvancedConfig(system.getRabbitmqConfig().getAdvancedRabbit()));
            root.put("advanced_config_ra", RabbitConfigGenerator.generateAdvancedConfig(system.getRabbitmqConfig().getAdvancedRa()));
            root.put("advanced_config_aten", RabbitConfigGenerator.generateAdvancedConfig(system.getRabbitmqConfig().getAdvancedAten()));

            if(system.getRabbitmqConfig().getPlugins() != null && !system.getRabbitmqConfig().getPlugins().isEmpty())
                root.put("plugins", system.getRabbitmqConfig().getPlugins());

            if(system.getRabbitmqConfig().getEnvConfig() != null && !system.getRabbitmqConfig().getEnvConfig().isEmpty())
                root.put("env_config",  RabbitConfigGenerator.generateEnvConfig(system.getRabbitmqConfig().getEnvConfig()));

            Writer out = new StringWriter();
            temp.process(root, out);

            String manifestContent = out.toString();
            return processExecutor.createFile(manifestContent, ".yaml");
        } catch(TemplateNotFoundException e) {
            LOGGER.error("The manifest template does not exist", e);
            throw new InvalidInputException("Could not generate manifest with template and data", e);
        } catch(MalformedTemplateNameException e) {
            LOGGER.error("The manifest template is malformed", e);
            throw new InvalidInputException("Could not generate manifest with template and data", e);
        } catch (ParseException e) {
            LOGGER.error("Could not parse the manifest template", e);
            throw new InvalidInputException("Could not generate manifest with template and data", e);
        } catch (TemplateException e) {
            LOGGER.error("An errored occurred generating the manifest", e);
            throw new InvalidInputException("Could not generate manifest with template and data", e);
        } catch (IOException e) {
            LOGGER.error("An errored occurred generating the manifest", e);
            throw new InvalidInputException("Could not generate manifest with template and data", e);
        }
    }

    private String toStr(Integer value) {
        DecimalFormat df = new DecimalFormat("#");
        return df.format(value);
    }
}
