import SwiftUI
import Combine

private enum Look {
    static let background=Color(red:4/255,green:13/255,blue:20/255)
    static let secondary=Color(red:163/255,green:186/255,blue:202/255)
    static let red=Color(red:244/255,green:12/255,blue:36/255)
    static let redDark=Color(red:142/255,green:2/255,blue:23/255)
    static let redBright=Color(red:1,green:27/255,blue:51/255)
    static let green=Color(red:0,green:122/255,blue:32/255)
    static let greenTop=Color(red:0,green:166/255,blue:22/255)
    static let yellow=Color(red:1,green:199/255,blue:22/255)
    static let yellowTop=Color(red:1,green:222/255,blue:37/255)
    static let neutral=Color(red:48/255,green:67/255,blue:82/255)
    static let neutralDark=Color(red:30/255,green:49/255,blue:64/255)
}

/// Same layout as Android 1.0.9: limit sign, speed in a square tile (grey unknown, green within,
/// yellow near, blinking red over), road, and a mute button that shows the CURRENT state.
struct SpeedDashboard:View {
    @ObservedObject var model:SpeedModel
    @Binding var showSettings:Bool
    @State private var flash=false
    private var speed:Double {model.speed ?? -1}
    private var allowed:Double {model.limit ?? -1}
    private var over:Bool {speed>=0 && allowed>0 && speed>allowed}
    private var near:Bool {speed>=0 && allowed>0 && speed>=allowed*0.95 && !over}
    private var tileColors:[Color] {
        if speed<0 || allowed<=0 {return [Look.neutral,Look.neutralDark]}
        if over {return flash ? [Look.redBright,Look.red] : [Look.red,Look.redDark]}
        return near ? [Look.yellowTop,Look.yellow] : [Look.greenTop,Look.green]
    }
    private var textTint:Color {near ? .black : .white}
    private var gpsBars:Int {
        guard let a=model.accuracy else{return 0}
        if a<=10 {return 4}
        if a<=20 {return 3}
        if a<=35 {return 2}
        return 1
    }
    private var version:String {
        Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? ""
    }

    var body:some View {
        GeometryReader { geometry in
            let landscape=geometry.size.width>geometry.size.height && geometry.size.width>560
            let dim:CGFloat=landscape ? 140 : (geometry.size.width<360 ? 150 : 170)
            ScrollView {
                VStack(spacing:14) {
                    header
                    statusLine
                    if landscape {
                        HStack(alignment:.center,spacing:22) {
                            HStack(spacing:14) {
                                SpeedLimitSign(limit:model.limit).frame(width:dim,height:dim)
                                speedTile(dim)
                            }
                            .frame(maxWidth:.infinity)
                            VStack(spacing:16) {
                                road
                                muteButton
                            }
                            .frame(maxWidth:.infinity)
                        }
                    } else {
                        SpeedLimitSign(limit:model.limit).frame(width:dim,height:dim)
                            .padding(.top,4)
                        Text("Velocidade atual").font(.headline)
                        speedTile(dim)
                        road.padding(.vertical,6)
                        muteButton
                    }
                    Text("A sinalização oficial sempre prevalece.")
                        .font(.caption2).foregroundStyle(.white.opacity(0.55))
                        .multilineTextAlignment(.center)
                        .padding(.top,3)
                }
                .padding(.horizontal,18)
                .padding(.top,12)
                .padding(.bottom,28)
                .frame(maxWidth:780)
                .frame(maxWidth:.infinity)
            }
            .background(Look.background.ignoresSafeArea())
        }
        .navigationBarHidden(true)
        .onReceive(Timer.publish(every:0.45,on:.main,in:.common).autoconnect()) { _ in
            flash = over ? !flash : false
        }
    }

    private var header:some View {
        HStack(spacing:10) {
            RadioMark().frame(width:50,height:50)
            VStack(alignment:.leading,spacing:0) {
                Text("PT2VHF").font(.system(size:23,weight:.black,design:.rounded)).italic()
                Text("Speed Alert").font(.system(size:16,weight:.medium))
            }
            Spacer(minLength:6)
            Text("iOS \(version)").font(.caption2).foregroundStyle(Look.secondary)
            GpsBars(level:gpsBars).frame(width:22,height:18)
            Button {showSettings=true} label: {
                Image(systemName:"gearshape.fill").font(.system(size:24))
                    .frame(width:44,height:44)
            }.accessibilityLabel("Configurações")
        }
    }
    private var statusLine:some View {
        HStack(spacing:8) {
            Circle().fill(model.monitoring ? Color(red:0,green:228/255,blue:67/255) : .orange)
                .frame(width:13,height:13)
            Text(model.monitoring ? model.status : "Monitoramento parado")
                .font(.subheadline)
        }
    }
    private func speedTile(_ dim:CGFloat)->some View {
        VStack(spacing:0) {
            Text(model.speed.map {String(Int($0.rounded()))} ?? "—")
                .font(.system(size:dim*0.42,weight:.bold,design:.rounded))
                .minimumScaleFactor(0.5)
                .lineLimit(1)
            Text("km/h").font(.title3.weight(.bold))
        }
        .foregroundStyle(textTint)
        .frame(width:dim,height:dim)
        .background(
            RoundedRectangle(cornerRadius:20)
                .fill(LinearGradient(colors:tileColors,startPoint:.top,endPoint:.bottom))
                .shadow(color:over && flash ? Look.red.opacity(0.8) : .black.opacity(0.35),
                        radius:over && flash ? 18 : 6)
        )
        .accessibilityElement(children:.ignore)
        .accessibilityLabel("Velocidade atual \(model.speed.map {Int($0.rounded()).description} ?? "desconhecida") quilômetros por hora")
    }
    private var road:some View {
        HStack(spacing:13) {
            Image(systemName:"road.lanes").font(.title).frame(width:34)
            VStack(alignment:.leading,spacing:4) {
                Text(model.roadName).font(.headline).lineLimit(2)
                Text(model.source).font(.caption).foregroundStyle(Look.secondary).lineLimit(2)
            }
            Spacer(minLength:0)
        }
        .frame(maxWidth:.infinity,alignment:.leading)
    }
    private var muteButton:some View {
        let muted=model.preferences.mute
        return Button {model.toggleMute()} label: {
            Label(muted ? "Som DESLIGADO" : "Som ligado",
                  systemImage:muted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                .font(.title3.weight(.bold))
                .frame(maxWidth:280,minHeight:55)
                .foregroundStyle(.white)
                .background(Capsule().fill(LinearGradient(
                    colors:muted ? [Look.red,Look.redDark]
                        : [Color(red:43/255,green:71/255,blue:92/255),Color(red:19/255,green:38/255,blue:52/255)],
                    startPoint:.top,endPoint:.bottom)))
                .overlay(Capsule().stroke(muted ? Color(red:1,green:174/255,blue:181/255)
                    : Color(red:104/255,green:146/255,blue:174/255),lineWidth:1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(muted ? "Som dos alarmes desligado. Toque para ligar."
                                  : "Som dos alarmes ligado. Toque para silenciar.")
    }
}

/// Discreet GPS quality indicator for the header.
private struct GpsBars:View {
    let level:Int
    var body:some View {
        HStack(alignment:.bottom,spacing:2) {
            ForEach(0..<4,id:\.self) { i in
                RoundedRectangle(cornerRadius:1)
                    .fill(i<level ? Color.green : Color.gray.opacity(0.5))
                    .frame(width:4,height:CGFloat(6+i*4))
            }
        }
        .frame(maxHeight:.infinity,alignment:.bottom)
        .accessibilityElement(children:.ignore)
        .accessibilityLabel("Sinal GPS: "+["aguardando","fraco","regular","bom","forte"][level])
    }
}

private struct SpeedLimitSign:View {
    let limit:Double?
    var body:some View {
        GeometryReader { g in
            let size=min(g.size.width,g.size.height)
            ZStack {
                Circle().fill(.white)
                Circle().stroke(Look.red,lineWidth:size*0.10)
                VStack(spacing:0) {
                    Text(limit.map {String(Int($0.rounded()))} ?? "—")
                        .font(.system(size:size*0.43,weight:.black,design:.rounded))
                        .minimumScaleFactor(0.65)
                    Text("km/h")
                        .font(.system(size:size*0.13,weight:.bold))
                }
                .foregroundStyle(.black)
            }
            .frame(width:size,height:size)
            .frame(maxWidth:.infinity,maxHeight:.infinity)
        }
        .accessibilityElement(children:.ignore)
        .accessibilityLabel("Limite da via \(limit.map {Int($0.rounded()).description} ?? "desconhecido")")
    }
}
private struct RadioMark:View {
    var body:some View {
        Canvas {ctx,size in
            for factor in [0.85,0.61,0.36] {
                var path=Path()
                let r=min(size.width,size.height)*factor*0.5
                let center=CGPoint(x:size.width/2,y:size.height*0.74)
                path.addArc(center:center,radius:r,startAngle:.degrees(190),endAngle:.degrees(350),clockwise:false)
                ctx.stroke(path,with:.color(Look.red),style:StrokeStyle(lineWidth:4.3,lineCap:.round))
            }
        }
    }
}
