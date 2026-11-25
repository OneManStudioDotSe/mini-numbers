import XCTest
@testable import CornerShop

final class PayloadTests: XCTestCase {
    private let session = String(repeating: "ab", count: 16)

    func testPageviewHasNoEventNameOrReferrer() {
        let body = MiniNumbers.payload(type: "pageview", path: "shop", sessionId: session, eventName: "oops")
        XCTAssertEqual(body["type"] as? String, "pageview")
        XCTAssertEqual(body["path"] as? String, "/shop")
        XCTAssertEqual(body["sessionId"] as? String, session)
        XCTAssertNil(body["eventName"])
        XCTAssertNil(body["referrer"])
    }

    func testPropertiesIsAStringHoldingJson() {
        let body = MiniNumbers.payload(type: "custom", path: "/events", sessionId: session,
                                       eventName: "signup", properties: ["plan": "free", "source": "sample-app"])
        XCTAssertEqual(body["properties"] as? String, #"{"plan":"free","source":"sample-app"}"#)
    }

    func testEmptyPropertiesAreOmitted() {
        let body = MiniNumbers.payload(type: "custom", path: "/events", sessionId: session, eventName: "x")
        XCTAssertNil(body["properties"])
    }

    func testPathHasNoSpaces() {
        XCTAssertEqual(MiniNumbers.cleanPath("/my page"), "/my-page")
    }

    func testSessionIdIs32LowercaseHex() {
        let id = MiniNumbers.makeSessionId()
        XCTAssertEqual(id.count, 32)
        XCTAssertNotNil(id.range(of: "^[0-9a-f]{32}$", options: .regularExpression))
    }

    func testUserAgentLooksLikeAnIPhone() {
        XCTAssertTrue(MiniNumbers.userAgent.hasPrefix("MiniNumbersSample/1.0 (iPhone; CPU iPhone OS "))
        XCTAssertTrue(MiniNumbers.userAgent.hasSuffix("Mobile"))
    }
}
