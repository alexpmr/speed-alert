import AVFoundation
import Foundation

enum AlarmStyle: String, CaseIterable, Identifiable {
    case beeps = "BIPES"
    case doubleBeep = "DUPLO"
    case tripleBeep = "TRIPLO"
    case siren = "SIRENE"
    case gentle = "SUAVE"
    case continuous = "CONTINUO"
    var id:String { rawValue }
    var label:String {
        switch self {
        case .beeps: return "Bipes repetitivos"
        case .doubleBeep: return "Bipes duplos"
        case .tripleBeep: return "Bipes triplos"
        case .siren: return "Sirene alternada"
        case .gentle: return "Alerta suave"
        case .continuous: return "Tom contínuo"
        }
    }
}

/// Loops synthesized alert tones while Core Location runs in the background.
/// Does not require a server or background audio streaming.
final class AlarmEngine {
    private let engine=AVAudioEngine()
    private let player=AVAudioPlayerNode()
    private var started=false
    private var currentKey=""
    /// Why the last start failed (shown by "Testar som"), nil when it worked.
    private(set) var lastError:String?
    private let sampleRate=22050.0

    init() {
        engine.attach(player)
        let format=AVAudioFormat(standardFormatWithSampleRate:sampleRate, channels:1)!
        engine.connect(player,to:engine.mainMixerNode,format:format)
    }
    private typealias Tone = (duration:Double, start:Double, end:Double)
    private func pattern(_ style:AlarmStyle)->[Tone] {
        switch style {
        case .beeps: return [(0.25,880,880),(0.35,0,0)]
        case .doubleBeep: return [(0.16,900,900),(0.12,0,0),(0.16,1080,1080),(0.45,0,0)]
        case .tripleBeep: return [(0.11,980,980),(0.09,0,0),(0.11,980,980),
                                   (0.09,0,0),(0.11,980,980),(0.42,0,0)]
        case .siren: return [(0.45,650,1160),(0.45,1160,650)]
        case .gentle: return [(0.25,620,620),(0.50,0,0)]
        case .continuous: return [(1.0,880,880)]
        }
    }
    private func buffer(for style:AlarmStyle)->AVAudioPCMBuffer? {
        let tones=pattern(style)
        let count=tones.reduce(0) { $0 + Int($1.duration*sampleRate) }
        guard count>0, let fmt=AVAudioFormat(standardFormatWithSampleRate:sampleRate,channels:1),
              let pcm=AVAudioPCMBuffer(pcmFormat:fmt,frameCapacity:AVAudioFrameCount(count)),
              let ch=pcm.floatChannelData?[0] else { return nil }
        pcm.frameLength=AVAudioFrameCount(count)
        var phase=0.0
        var offset=0
        for tone in tones {
            let length=Int(tone.duration*sampleRate)
            for i in 0..<length {
                let frequency=tone.start+(tone.end-tone.start)*Double(i)/Double(length)
                var value=0.0
                if tone.start>0 {
                    phase+=2*Double.pi*frequency/sampleRate
                    let envelope=style == .continuous ? 1.0 :
                        min(1,Double(min(i,length-i-1))/150)
                    value=sin(phase)*max(0,envelope)*0.65
                }
                ch[offset]=Float(value)
                offset+=1
            }
        }
        return pcm
    }
    func play(_ active:Bool,style:AlarmStyle,volume:Double) {
        if !active || volume<=0 { stop(); return }
        let key=style.rawValue
        // A phone call or Siri stops the engine without telling us: restart it then.
        if currentKey==key && started && engine.isRunning && player.isPlaying {
            player.volume=Float(volume/100)
            return
        }
        stop()
        guard let pcm=buffer(for:style) else {return}
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback,
                mode:.default,options:[.mixWithOthers,.duckOthers])
            try AVAudioSession.sharedInstance().setActive(true)
            player.scheduleBuffer(pcm,at:nil,options:[.loops],completionHandler:nil)
            try engine.start()
            player.volume=Float(volume/100)
            player.play()
            currentKey=key
            started=true
            lastError=nil
        }catch{
            NSLog("PT2VHF: alert audio error %@",String(describing:error))
            lastError=error.localizedDescription
            stop()
        }
    }
    func stop() {
        player.stop()
        engine.stop()
        if started { try? AVAudioSession.sharedInstance().setActive(false,options:.notifyOthersOnDeactivation) }
        started=false
        currentKey=""
    }
    deinit {stop()}
}
