/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirPureAbstractElement
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.symbols.StandardClassIds
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.ClassId

sealed class FirImplicitBuiltinTypeRef(
    override val psi: PsiElement?,
    val id: ClassId,
    typeArguments: Array<out ConeKotlinTypeProjection> = emptyArray(),
    isNullable: Boolean = false
) : FirPureAbstractElement(), FirResolvedTypeRef {
    override val annotations: List<FirAnnotationCall>
        get() = emptyList()

    override val type: ConeClassLikeType = ConeClassTypeImpl(ConeClassLikeLookupTagImpl(id), typeArguments, isNullable)

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        return this
    }
}

class FirImplicitUnitTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Unit)

class FirImplicitAnyTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Any)

class FirImplicitNullableAnyTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Any, isNullable = true)

class FirImplicitEnumTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Enum)

class FirImplicitAnnotationTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Annotation)

class FirImplicitBooleanTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Boolean)

class FirImplicitNothingTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Nothing)

class FirImplicitNullableNothingTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.Nothing, isNullable = true)

class FirImplicitStringTypeRef(
    psi: PsiElement?
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.String)

class FirImplicitKPropertyTypeRef(
    psi: PsiElement?,
    typeArgument: ConeKotlinTypeProjection
) : FirImplicitBuiltinTypeRef(psi, StandardClassIds.KProperty, arrayOf(typeArgument))