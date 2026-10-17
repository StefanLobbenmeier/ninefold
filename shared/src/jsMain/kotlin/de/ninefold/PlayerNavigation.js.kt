package de.ninefold
import androidx.compose.runtime.*
private fun subscribeBack(onBack: () -> Unit): () -> Unit = js("""((onBack) => {
    let popped = false;
    history.pushState({ninefoldPlayer: true}, '', location.href);
    const listener = () => { popped = true; onBack(); };
    window.addEventListener('popstate', listener);
    return () => {
        window.removeEventListener('popstate', listener);
        if (!popped && history.state && history.state.ninefoldPlayer) history.back();
    };
})(onBack)""")
@Composable
internal actual fun PlayerBackHandler(onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val unsubscribe = subscribeBack { current() }
        onDispose { unsubscribe() }
    }
}
