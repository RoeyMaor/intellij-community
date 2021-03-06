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
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 *
 * @since 11/24/2016
 */
class GradleMiscContributor : GradleMethodContextContributor {
  companion object {
    val useJUnitClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_TASKS_TESTING_TEST, "useJUnit"))
    val testLoggingClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_TASKS_TESTING_TEST, "testLogging"))
    val downloadClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "download"))
    val domainCollectionWithTypeClosure = groovyClosure().inMethod(psiMethod("org.gradle.api.DomainObjectCollection", "withType"))

    val downloadSpecFqn = "de.undercouch.gradle.tasks.download.DownloadSpec"
    val pluginDependenciesSpecFqn = "org.gradle.plugin.use.PluginDependenciesSpec"
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (useJUnitClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_JUNIT_OPTIONS, closure), Closure.DELEGATE_FIRST)
    }
    if (testLoggingClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(GRADLE_API_TEST_LOGGING_CONTAINER, closure), Closure.DELEGATE_FIRST)
    }
    if (downloadClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(downloadSpecFqn, closure), Closure.DELEGATE_FIRST)
    }

    if (domainCollectionWithTypeClosure.accepts(closure)) {
      val parent = closure.parent
      if (parent is GrMethodCallExpression) {
        val psiElement = parent.argumentList.allArguments.singleOrNull()?.reference?.resolve()
        if (psiElement is PsiClass) {
          return DelegatesToInfo(TypesUtil.createType(psiElement.qualifiedName, closure), Closure.DELEGATE_FIRST)
        }
      }
    }
    return null
  }

  override fun process(methodCallInfo: MutableList<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {

    val classHint = processor.getHint(com.intellij.psi.scope.ElementClassHint.KEY)
    val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
    val psiManager = GroovyPsiManager.getInstance(place.project)
    val resolveScope = place.resolveScope

    if (shouldProcessMethods && place.parent?.parent is GroovyFile && place.text == "plugins") {
      val pluginsDependenciesClass = psiManager.findClassWithCache(pluginDependenciesSpecFqn, resolveScope) ?: return true
      val returnClass = psiManager.createTypeByFQClassName(pluginDependenciesSpecFqn, resolveScope) ?: return true
      val methodBuilder = GrLightMethodBuilder(place.manager, "plugins").apply {
        containingClass = pluginsDependenciesClass
        returnType = returnClass
      }
      methodBuilder.addAndGetParameter("configuration", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, pluginDependenciesSpecFqn)
      place.putUserData(RESOLVED_CODE, true)
      if (!processor.execute(methodBuilder, state)) return false
    }

    if (psiElement().inside(domainCollectionWithTypeClosure).accepts(place)) {

    }
    return true
  }
}
