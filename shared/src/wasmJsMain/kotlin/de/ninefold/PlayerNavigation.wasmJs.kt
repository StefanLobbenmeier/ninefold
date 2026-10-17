@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.ninefold
import androidx.compose.runtime.*
@JsFun("""(onBack) => {
    let popped = false;
    history.pushState({ninefoldPlayer: true}, '', location.href);
    const listener = () => { popped = true; onBack(); };
    window.addEventListener('popstate', listener);
    return () => {
        window.removeEventListener('popstate', listener);
        if (!popped && history.state?.ninefoldPlayer) history.back();
    };
}""")
private external fun subscribeBack(onBack: () -> Unit): JsAny
@JsFun("(unsubscribe) => unsubscribe()")
private external fun unsubscribeBack(unsubscribe: JsAny)
@Composable
internal actual fun PlayerBackHandler(onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val unsubscribe = subscribeBack { current() }
        onDispose { unsubscribeBack(unsubscribe) }
    }
}
