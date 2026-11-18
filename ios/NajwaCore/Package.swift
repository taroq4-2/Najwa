// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "NajwaCore",
    defaultLocalization: "ar",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "NajwaCore", targets: ["NajwaCore"])],
    targets: [
        .target(name: "NajwaCore"),
        .testTarget(name: "NajwaCoreTests", dependencies: ["NajwaCore"], resources: [.copy("Resources/golden.json")]),
    ]
)
