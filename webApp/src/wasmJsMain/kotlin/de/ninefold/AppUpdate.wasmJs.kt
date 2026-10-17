@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.ninefold

@JsFun("(onChange) => window.ninefoldUpdate.subscribe(onChange)")
private external fun subscribeUpdate(onChange: (String) -> Unit): JsAny

@JsFun("(unsubscribe) => unsubscribe()")
private external fun unsubscribeUpdate(unsubscribe: JsAny)

internal actual fun observeAppUpdate(onChange: (String) -> Unit): () -> Unit {
    val unsubscribe = subscribeUpdate(onChange)
    return { unsubscribeUpdate(unsubscribe) }
}

@JsFun("() => window.ninefoldUpdate.apply()")
internal actual external fun applyAppUpdate()
