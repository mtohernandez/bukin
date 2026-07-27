// The BukIn wire protocol, in Swift.
//
// This must agree byte for byte with `domain/.../crypto/RotatingCode.kt` and
// `AdvertisementPayload.kt`, and with the pgcrypto expression session 3 verifies against.
// The three implementations can never call each other, so the known vector below is the
// only thing holding them together — `selfCheck()` runs it on every start.
//
// Shared by beacon.swift and scan.swift; compile it alongside them.
import Foundation
import CryptoKit

enum BukIn {

    /// "BUKN". Constant, and what the Android scan filter matches on.
    static let magic: [UInt8] = [0x42, 0x55, 0x4B, 0x4E]

    static let windowSeconds: Int64 = 30
    static let codeBytes = 8

    /// The known vector. Same numbers as RotatingCodeTest.
    static let vectorKey: [UInt8] = Array(0...15)
    static let vectorInstancia: UInt32 = 42
    static let vectorCounter: Int64 = 58_000_000
    static let vectorCode = "67e94bf8a08959ea"

    static func counter(forUnixSeconds seconds: Int64, clockOffset: Int64 = 0) -> Int64 {
        let t = seconds + clockOffset
        // Floor division, so pre-epoch times do not round the wrong way.
        return t >= 0 ? t / windowSeconds : ((t + 1) / windowSeconds) - 1
    }

    /// HMAC-SHA256(key, instanciaId ‖ counter)[0..7], both operands big-endian.
    static func derive(key: [UInt8], instanciaId: UInt32, counter: Int64) -> [UInt8] {
        var message = [UInt8]()
        message.append(contentsOf: withUnsafeBytes(of: instanciaId.bigEndian) { Array($0) })
        message.append(contentsOf: withUnsafeBytes(of: counter.bigEndian) { Array($0) })
        let mac = HMAC<SHA256>.authenticationCode(
            for: Data(message),
            using: SymmetricKey(data: Data(key))
        )
        return Array(Array(mac).prefix(codeBytes))
    }

    /// 42554B4E-XXXX-XXXX-YYYY-YYYYYYYYYYYY
    static func uuidString(instanciaId: UInt32, code: [UInt8]) -> String {
        var bytes = magic
        bytes.append(contentsOf: withUnsafeBytes(of: instanciaId.bigEndian) { Array($0) })
        bytes.append(contentsOf: code)
        let hex = bytes.map { String(format: "%02X", $0) }.joined()
        let i = { (n: Int) in hex.index(hex.startIndex, offsetBy: n) }
        return "\(hex[i(0)..<i(8)])-\(hex[i(8)..<i(12)])-\(hex[i(12)..<i(16)])-"
             + "\(hex[i(16)..<i(20)])-\(hex[i(20)..<i(32)])"
    }

    /// Reverse of `uuidString`. Returns nil for anything that is not a BukIn advertisement.
    static func decode(uuidString: String) -> (instanciaId: UInt32, code: [UInt8])? {
        let hex = uuidString.replacingOccurrences(of: "-", with: "").uppercased()
        guard hex.count == 32 else { return nil }
        var bytes = [UInt8]()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        guard Array(bytes[0..<4]) == magic else { return nil }
        let instancia = bytes[4..<8].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        return (instancia, Array(bytes[8..<16]))
    }

    static func hex(_ bytes: [UInt8]) -> String {
        bytes.map { String(format: "%02x", $0) }.joined()
    }

    static func parseHexKey(_ string: String) -> [UInt8]? {
        let s = string.replacingOccurrences(of: " ", with: "")
        guard s.count % 2 == 0, !s.isEmpty else { return nil }
        var bytes = [UInt8]()
        var index = s.startIndex
        while index < s.endIndex {
            let next = s.index(index, offsetBy: 2)
            guard let byte = UInt8(s[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        return bytes
    }

    /// Fails loudly at startup rather than quietly advertising codes Android will reject.
    static func selfCheck() {
        let produced = hex(derive(key: vectorKey, instanciaId: vectorInstancia, counter: vectorCounter))
        guard produced == vectorCode else {
            print("SELF-CHECK FAILED: expected \(vectorCode), got \(produced)")
            print("  This Swift no longer agrees with :domain's RotatingCodeTest. Stop.")
            exit(10)
        }
        print("self-check OK — known vector \(produced) matches :domain")
    }

    /// `--key <hex>` and `--instancia <int>`, shared by both tools. Exits on anything else.
    static func parseArguments() -> (key: [UInt8]?, instanciaId: UInt32?) {
        var key: [UInt8]? = nil
        var instanciaId: UInt32? = nil
        var arguments = Array(CommandLine.arguments.dropFirst())

        while let flag = arguments.first {
            arguments.removeFirst()
            switch flag {
            case "--key":
                guard let raw = arguments.first, let parsed = parseHexKey(raw) else {
                    print("--key needs a hex string"); exit(64)
                }
                key = parsed
                arguments.removeFirst()
            case "--instancia":
                guard let raw = arguments.first, let parsed = UInt32(raw) else {
                    print("--instancia needs a number"); exit(64)
                }
                instanciaId = parsed
                arguments.removeFirst()
            default:
                print("unknown flag \(flag) — expected --key <hex> or --instancia <int>")
                exit(64)
            }
        }
        return (key, instanciaId)
    }

    static func timestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter.string(from: Date())
    }
}
