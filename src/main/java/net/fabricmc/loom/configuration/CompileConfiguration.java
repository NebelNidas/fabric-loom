/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration;

import java.nio.charset.StandardCharsets;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.mixin.JavaApInvoker;
import net.fabricmc.loom.build.mixin.KaptApInvoker;
import net.fabricmc.loom.build.mixin.ScalaApInvoker;
import net.fabricmc.loom.configuration.ide.SetupIntelijRunConfigs;
import net.fabricmc.loom.configuration.providers.LaunchProvider;
import net.fabricmc.loom.configuration.providers.MinecraftProviderImpl;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.util.Constants;

public final class CompileConfiguration {
	private CompileConfiguration() {
	}

	public static void setupConfigurations(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);

		extension.createLazyConfiguration(Constants.Configurations.MOD_COMPILE_CLASSPATH).configure(configuration -> configuration.setTransitive(true));
		extension.createLazyConfiguration(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED).configure(configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_NAMED).configure(configuration -> configuration.setTransitive(false)); // The launchers do not recurse dependencies
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT_DEPENDENCIES).configure(configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.LOADER_DEPENDENCIES).configure(configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.MINECRAFT).configure(configuration -> configuration.setTransitive(false));
		extension.createLazyConfiguration(Constants.Configurations.INCLUDE).configure(configuration -> configuration.setTransitive(false)); // Dont get transitive deps
		extension.createLazyConfiguration(Constants.Configurations.MAPPING_CONSTANTS);
		extension.createLazyConfiguration(Constants.Configurations.NAMED_ELEMENTS).configure(configuration -> {
			configuration.setCanBeConsumed(true);
			configuration.setCanBeResolved(false);
			configuration.extendsFrom(project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
		});

		extendsFrom(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Configurations.MAPPING_CONSTANTS, project);

		extension.createLazyConfiguration(Constants.Configurations.MAPPINGS);
		extension.createLazyConfiguration(Constants.Configurations.MAPPINGS_FINAL);
		extension.createLazyConfiguration(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
		extension.createLazyConfiguration(Constants.Configurations.UNPICK_CLASSPATH);
		extension.createLazyConfiguration(Constants.Configurations.LOCAL_RUNTIME);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOCAL_RUNTIME, project);

		for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
			extension.createLazyConfiguration(entry.sourceConfiguration())
					.configure(configuration -> configuration.setTransitive(true));

			// Don't get transitive deps of already remapped mods
			extension.createLazyConfiguration(entry.getRemappedConfiguration())
					.configure(configuration -> configuration.setTransitive(false));

			if (entry.compileClasspath()) {
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH, entry.sourceConfiguration(), project);
				extendsFrom(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, entry.getRemappedConfiguration(), project);
				extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
				extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
			}

			if (entry.runtimeClasspath()) {
				extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
				extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, entry.getRemappedConfiguration(), project);
			}

			for (String outgoingConfiguration : entry.publishingMode().outgoingConfigurations()) {
				extendsFrom(outgoingConfiguration, entry.sourceConfiguration(), project);
			}
		}

		extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_NAMED, project);

		extendsFrom(Constants.Configurations.LOADER_DEPENDENCIES, Constants.Configurations.MINECRAFT_DEPENDENCIES, project);
		extendsFrom(Constants.Configurations.MINECRAFT_NAMED, Constants.Configurations.LOADER_DEPENDENCIES, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL, project);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, project);
	}

	public static void configureCompile(Project p) {
		JavaPluginConvention javaModule = (JavaPluginConvention) p.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		Javadoc javadoc = (Javadoc) p.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		p.afterEvaluate(project -> {
			LoomGradleExtension extension = LoomGradleExtension.get(project);

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);

			dependencyManager.addProvider(new MinecraftProviderImpl(project));
			dependencyManager.addProvider(new MappingsProviderImpl(project));
			dependencyManager.addProvider(new LaunchProvider(project));

			dependencyManager.handleDependencies(project);

			project.getTasks().getByName("idea").finalizedBy(project.getTasks().getByName("genIdeaWorkspace"));
			project.getTasks().getByName("eclipse").finalizedBy(project.getTasks().getByName("genEclipseRuns"));
			project.getTasks().getByName("cleanEclipse").finalizedBy(project.getTasks().getByName("cleanEclipseRuns"));

			SetupIntelijRunConfigs.setup(project);
			extension.getRemapArchives().finalizeValue();

			// Enables the default mod remapper
			if (extension.getRemapArchives().get()) {
				RemapConfiguration.setupDefaultRemap(project);
			} else {
				Jar jarTask = (Jar) project.getTasks().getByName("jar");
				extension.getUnmappedModCollection().from(jarTask);
			}

			MixinExtension mixin = LoomGradleExtension.get(project).getMixin();

			if (mixin.getUseLegacyMixinAp().get()) {
				setupMixinAp(project, mixin);
			}
		});

		// Add the "dev" jar to the "namedElements" configuration
		p.artifacts(artifactHandler -> artifactHandler.add(Constants.Configurations.NAMED_ELEMENTS, p.getTasks().getByName("jar")));

		// Ensure that the encoding is set to UTF-8, no matter what the system default is
		// this fixes some edge cases with special characters not displaying correctly
		// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
		p.getTasks().withType(AbstractCopyTask.class).configureEach(abstractCopyTask -> abstractCopyTask.setFilteringCharset(StandardCharsets.UTF_8.name()));
		p.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> javaCompile.getOptions().setEncoding(StandardCharsets.UTF_8.name()));

		if (p.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			// If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
			throw new IllegalArgumentException("fabric-loom must be applied BEFORE kapt in the plugins { } block.");
		}
	}

	private static void setupMixinAp(Project project, MixinExtension mixin) {
		mixin.init();

		// Disable some things used by log4j via the mixin AP that prevent it from being garbage collected
		System.setProperty("log4j2.disable.jmx", "true");
		System.setProperty("log4j.shutdownHookEnabled", "false");
		System.setProperty("log4j.skipJansi", "true");

		project.getLogger().info("Configuring compiler arguments for Java");

		new JavaApInvoker(project).configureMixin();

		if (project.getPluginManager().hasPlugin("scala")) {
			project.getLogger().info("Configuring compiler arguments for Scala");
			new ScalaApInvoker(project).configureMixin();
		}

		if (project.getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
			project.getLogger().info("Configuring compiler arguments for Kapt plugin");
			new KaptApInvoker(project).configureMixin();
		}
	}

	private static void extendsFrom(String a, String b, Project project) {
		project.getConfigurations().getByName(a, configuration -> configuration.extendsFrom(project.getConfigurations().getByName(b)));
	}
}
