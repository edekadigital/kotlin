/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

interface FirResolvedImport : FirImport {
    override val psi: PsiElement?
    override val importedFqName: FqName?
    override val isAllUnder: Boolean
    override val aliasName: Name?
    val delegate: FirImport
    val packageFqName: FqName
    val relativeClassName: FqName?
    val resolvedClassId: ClassId?
    val importedName: Name?

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R = visitor.visitResolvedImport(this, data)
}
