import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose UI from the Kotlin `composeApp` module.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // Sign-in redirects (funpixelnest://login#…) go back to Supabase in Kotlin.
            .onOpenURL { url in MainViewControllerKt.handleOpenUrl(url: url) }
            .background(Color(red: 217 / 255, green: 240 / 255, blue: 255 / 255))
    }
}
