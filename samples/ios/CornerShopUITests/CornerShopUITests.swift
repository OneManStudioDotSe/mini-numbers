import XCTest

/// Walks the whole flow against a real Mini Numbers server. Pass the key with `-projectKey` (here a launch
/// argument; set the PROJECT_KEY environment variable of the test run to override the default).
final class CornerShopUITests: XCTestCase {
    func testWalkThrough() {
        let app = XCUIApplication()
        let key = ProcessInfo.processInfo.environment["PROJECT_KEY"] ?? ""
        app.launchArguments = ["-serverUrl", "http://localhost:8092", "-projectKey", key]
        app.launch()

        // Shop -> Mug -> Buy -> Refund -> back
        app.buttons["product-mug"].tap()
        app.buttons["Buy"].tap()
        XCTAssertTrue(app.staticTexts["Sent purchase · €12.00"].waitForExistence(timeout: 2))
        app.buttons["Refund"].tap()
        XCTAssertTrue(app.staticTexts["Sent refund · -€12.00"].waitForExistence(timeout: 2))
        app.buttons["back"].tap()
        XCTAssertTrue(app.buttons["product-notebook"].waitForExistence(timeout: 2))

        // Events -> Sign up -> Open the docs
        app.buttons["tab-events"].tap()
        app.buttons["Sign up"].tap()
        XCTAssertTrue(app.staticTexts["signup"].waitForExistence(timeout: 2))
        app.buttons["Open the docs"].tap()
        sleep(2)
        app.activate()

        // Settings -> Don't track me -> move around: everything must say "not sent"
        app.buttons["tab-settings"].tap()
        let toggle = app.switches.firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 2))
        toggle.tap()
        app.buttons["tab-shop"].tap()
        app.buttons["product-mug"].tap()
        app.buttons["Buy"].tap()
        app.buttons["tab-events"].tap()
        XCTAssertTrue(app.staticTexts["not sent"].firstMatch.waitForExistence(timeout: 2))

        // Leave the app the way it started.
        app.buttons["tab-settings"].tap()
        app.switches.firstMatch.tap()
    }
}
