@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.sudokupad

@JsFun("""(onChange) => {
    const listener = () => onChange(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', listener);
    listener();
    return () => document.removeEventListener('visibilitychange', listener);
}""")
private external fun subscribeVisibility(onChange: (Boolean) -> Unit): JsAny

@JsFun("(unsubscribe) => unsubscribe()")
private external fun unsubscribeVisibility(unsubscribe: JsAny)

internal actual fun observePageVisibility(onChange: (Boolean) -> Unit): () -> Unit {
    val unsubscribe = subscribeVisibility(onChange)
    return { unsubscribeVisibility(unsubscribe) }
}
