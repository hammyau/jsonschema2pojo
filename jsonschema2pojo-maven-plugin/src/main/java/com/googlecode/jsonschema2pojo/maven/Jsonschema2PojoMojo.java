/**
 * Copyright © 2010-2011 Nokia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.jsonschema2pojo.maven;

import static org.apache.commons.lang.StringUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.googlecode.jsonschema2pojo.AnnotationStyle;
import com.googlecode.jsonschema2pojo.GenerationConfig;
import com.googlecode.jsonschema2pojo.cli.Jsonschema2Pojo;

/**
 * When invoked, this goal reads one or more <a
 * href="http://json-schema.org/">JSON Schema</a> documents and generates DTO
 * style Java classes for data binding.
 * 
 * @goal generate
 * @phase generate-sources
 * @requiresDependencyResolution compile
 * @see <a
 *      href="http://maven.apache.org/developers/mojo-api-specification.html">Mojo
 *      API Specification</a>
 */
public class Jsonschema2PojoMojo extends AbstractMojo implements
        GenerationConfig {

    /**
     * Target directory for generated Java source files.
     * 
     * @parameter expression="${jsonschema2pojo.outputDirectory}"
     *            default-value="${project.build.directory}/java-gen"
     * @since 0.1.0
     */
    private File outputDirectory;

    /**
     * Location of the JSON Schema file(s). Note: this may refer to a single
     * file or a directory of files.
     * 
     * @parameter expression="${jsonschema2pojo.sourceDirectory}"
     * @since 0.1.0
     */
    private File sourceDirectory;

    /**
     * An array of locations of the JSON Schema file(s). Note: each item may
     * refer to a single file or a directory of files.
     * 
     * @parameter expression="${jsonschema2pojo.sourcePaths}"
     * @since 0.3.1
     */
    private File[] sourcePaths;

    /**
     * Package name used for generated Java classes (for types where a fully
     * qualified name has not been supplied in the schema using the 'javaType'
     * property).
     * 
     * @parameter expression="${jsonschema2pojo.targetPackage}"
     * @since 0.1.0
     */
    private String targetPackage = "";

    /**
     * Whether to generate builder-style methods of the form
     * <code>withXxx(value)</code> (that return <code>this</code>), alongside
     * the standard, void-return setters.
     * 
     * @parameter expression="${jsonschema2pojo.generateBuilders}"
     *            default-value="false"
     * @since 0.1.2
     */
    private boolean generateBuilders = false;

    /**
     * Whether to use primitives (<code>long</code>, <code>double</code>,
     * <code>boolean</code>) instead of wrapper types where possible when
     * generating bean properties (has the side-effect of making those
     * properties non-null).
     * 
     * @parameter expression="${jsonschema2pojo.usePrimitives}"
     *            default-value="false"
     * @since 0.2.0
     */
    private boolean usePrimitives = false;

    /**
     * Add the output directory to the project as a source root, so that the
     * generated java types are compiled and included in the project artifact.
     * 
     * @parameter expression="${jsonschema2pojo.addCompileSourceRoot}"
     *            default-value="true"
     * @since 0.1.9
     */
    private boolean addCompileSourceRoot = true;

    /**
     * Skip plugin execution (don't read/validate any schema files, don't
     * generate any java types).
     * 
     * @parameter expression="${jsonschema2pojo.skip}" default-value="false"
     * @since 0.2.1
     */
    private boolean skip = false;

    /**
     * The characters that should be considered as word delimiters when creating
     * Java Bean property names from JSON property names. If blank or not set,
     * JSON properties will be considered to contain a single word when creating
     * Java Bean property names.
     * 
     * @parameter expression="${jsonschema2pojo.propertyWordDelimiters}"
     *            default-value=""
     * @since 0.2.2
     */
    private String propertyWordDelimiters = "";

    /**
     * Whether to use the java type <code>long</code> (or <code>Long</code>)
     * instead of <code>int</code> (or <code>Integer</code>) when representing
     * the JSON Schema type 'integer'.
     * 
     * @parameter expression="${jsonschema2pojo.useLongIntegers}"
     *            default-value="false"
     * @since 0.2.2
     */
    private boolean useLongIntegers = false;

    /**
     * Whether to include <code>hashCode</code> and <code>equals</code> methods
     * in generated Java types.
     * 
     * @parameter expression="${jsonschema2pojo.includeHashcodeAndEquals}"
     *            default-value="true"
     * @since 0.3.1
     */
    private boolean includeHashcodeAndEquals = true;

    /**
     * Whether to include a <code>toString</code> method in generated Java
     * types.
     * 
     * @parameter expression="${jsonschema2pojo.includeToString}"
     *            default-value="true"
     * @since 0.3.1
     */
    private boolean includeToString = true;

    /**
     * The style of annotations to use in the generated Java types.
     * <p>
     * Supported values:
     * <ul>
     * <li>
     * <code>jackson2</code> (apply annotations from the <a
     * href="https://github.com/FasterXML/jackson-annotations">Jackson 2.x</a>
     * library)</li>
     * <li>
     * <code>jackson1</code> (apply annotations from the <a
     * href="http://jackson.codehaus.org/">Jackson 1.x</a> library)</li>
     * <li>
     * <code>none</code> (apply no annotations at all)</li>
     * </ul>
     * 
     * @parameter expression="${jsonschema2pojo.annotationStyle}"
     *            default-value="jackson2"
     * @since 0.3.1
     */
    private String annotationStyle = "jackson";

    /**
     * The project being built.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Executes the plugin, to read the given source and behavioural properties
     * and generate POJOs. The current implementation acts as a wrapper around
     * the command line interface.
     */
    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = {
            "NP_UNWRITTEN_FIELD", "UWF_UNWRITTEN_FIELD" }, justification = "Private fields set by Maven.")
    public void execute() throws MojoExecutionException {

        try {
            AnnotationStyle.valueOf(upperCase(annotationStyle));
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Not a valid annotation style: " + annotationStyle);
        }

        if (skip) {
            return;
        }

        if (null == sourceDirectory && null == sourcePaths) {
            throw new MojoExecutionException("One of sourceDirectory or sourcePaths must be provided");
        }

        if (addCompileSourceRoot) {
            project.addCompileSourceRoot(outputDirectory.getPath());
        }

        addProjectDependenciesToClasspath();

        try {
            Jsonschema2Pojo.generate(this);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Error generating classes from JSON Schema file(s) " + sourceDirectory.getPath(), e);
        }

    }

    private void addProjectDependenciesToClasspath() {

        try {

            ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader newClassLoader = new ProjectClasspath().getClassLoader(project, oldClassLoader, getLog());
            Thread.currentThread().setContextClassLoader(newClassLoader);

        } catch (DependencyResolutionRequiredException e) {
            getLog().info(
                    "Skipping addition of project artifacts, there appears to be a dependecy resolution problem",
                    e);
        }

    }

    @Override
    public boolean isGenerateBuilders() {
        return generateBuilders;
    }

    @Override
    public File getTargetDirectory() {
        return outputDirectory;
    }

    @Override
    public Iterator<File> getSource() {
        if (null != sourceDirectory) {
            return Collections.singleton(sourceDirectory).iterator();
        }
        return Arrays.asList(sourcePaths).iterator();
    }

    @Override
    public boolean isUsePrimitives() {
        return usePrimitives;
    }

    @Override
    public String getTargetPackage() {
        return targetPackage;
    }

    @Override
    public char[] getPropertyWordDelimiters() {
        return propertyWordDelimiters.toCharArray();
    }

    @Override
    public boolean isUseLongIntegers() {
        return useLongIntegers;
    }

    @Override
    public boolean isIncludeHashcodeAndEquals() {
        return includeHashcodeAndEquals;
    }

    @Override
    public boolean isIncludeToString() {
        return includeToString;
    }

    @Override
    public AnnotationStyle getAnnotationStyle() {
        return AnnotationStyle.valueOf(annotationStyle.toUpperCase());
    }
}
