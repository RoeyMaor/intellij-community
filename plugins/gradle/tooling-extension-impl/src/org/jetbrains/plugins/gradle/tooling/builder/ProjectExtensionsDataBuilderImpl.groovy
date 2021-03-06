/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.tooling.builder

import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.internal.plugins.DefaultConvention
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.DefaultGradleExtension
import org.jetbrains.plugins.gradle.model.DefaultGradleExtensions
import org.jetbrains.plugins.gradle.model.DefaultGradleProperty
import org.jetbrains.plugins.gradle.model.GradleExtensions
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
@CompileStatic
class ProjectExtensionsDataBuilderImpl implements ModelBuilderService {
  @Override
  boolean canBuild(String modelName) {
    modelName == GradleExtensions.name
  }

  @Override
  Object buildAll(String modelName, Project project) {
    DefaultGradleExtensions result = new DefaultGradleExtensions()
    result.parentProjectDir = project.parent?.projectDir

    def conventions = project.extensions as DefaultConvention
    conventions.extraProperties.properties.each { name, value ->
      if(name == 'extraModelBuilder') return
      String typeFqn = getType(value)
      result.gradleProperties.add(new DefaultGradleProperty(
        name, typeFqn, value.toString()))
    }

    for (it in conventions.findAll()) {
      def convention = it as DefaultConvention
      convention.asMap.each { name, value ->
        if(name == 'idea') return
        def rootTypeFqn = getType(value)
        def namedObjectTypeFqn = null as String
        if (value instanceof NamedDomainObjectCollection) {
          def objectCollection = (NamedDomainObjectCollection)value
          if(!objectCollection.isEmpty()) {
            namedObjectTypeFqn = getType(objectCollection.first())
          }
        }
        result.extensions.add(new DefaultGradleExtension(name, rootTypeFqn, namedObjectTypeFqn))
      }
    }
    return result
  }

  @Override
  ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Project extensions data import errors"
    ).withDescription(
      "Unable to resolve some context data of gradle scripts. Some codeInsight features inside *.gradle files can be unavailable.")
  }

  static String getType(object) {
    def clazz = object?.getClass()?.canonicalName
    def decorIndex = clazz?.lastIndexOf('_Decorated')
    def result = !decorIndex || decorIndex == -1 ? clazz : clazz.substring(0, decorIndex)
    if(!result && object instanceof Closure) return "groovy.lang.Closure"
    return result
  }
}
