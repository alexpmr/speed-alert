import SwiftUI
import Combine

private enum Look {
    static let background=Color(red:0.018,green:0.050,blue:0.077)
    static let panel=Color(red:0.075,green:0.14,blue:0.19)
    static let red=Color(red:0.97,green:0.06,blue:0.14)
    static let green=Color(red:0.0,green:0.53,blue:0.11)
    static let yellow=Color(red:1,green:0.81,blue:0.1)
}

struct SpeedDashboard:View {
    @ObservedObject var model:SpeedModel
    @Binding var showSettings:Bool
    @State private var flash=false
    private var speed:Double {model.speed ?? -1}
    private var allowed:Double {model.limit ?? -1}
    private var over:Bool {speed>=0 && allowed>0 && speed>allowed}
    private var near:Bool {speed>=0 && allowed>0 && speed>=allowed*0.95 && !over}
    private var speedTint:Color {
        if speed<0 || allowed<=0 {return .gray.opacity(0.45)}
        if over {return flash ? Look.red : Color(red:0.50,green:0.0,blue:0.055)}
        return near ? Look.yellow : Look.green
    }
    private var textTint:Color {near ? .black : .white}
    private var gpsBars:Int {
        guard let a=model.accuracy else{return 0}
        if a<=10 {return 4}
        if a<=20 {return 3}
        if a<=35 {return 2}
        return 1
    }
    private var gpsLabel:String {["Aguarde","Fraco","Regular","Bom","Forte"][gpsBars]}

    var body:some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing:18) {
                    header
                    HStack(spacing:9) {
                        Circle().fill(model.monitoring ? .green : .orange)
                            .frame(width:15,height:15)
                        Text(model.monitoring ? "Monitoramento ativo" : model.status)
                            .font(.subheadline)
                    }
                    .padding(.bottom,2)

                    if geometry.size.width>650 {
                        HStack(alignment:.center,spacing:22) {
                            VStack(spacing:12) {
                                SpeedLimitSign(limit:model.limit).frame(width:190,height:190)
                                Text("Velocidade atual").font(.headline)
                                speedBanner
                            }
                            .frame(maxWidth:.infinity)
                            VStack(spacing:22) {
                                road
                                metrics
                                muteButton
                            }
                            .frame(maxWidth:.infinity)
                        }
                    } else {
                        SpeedLimitSign(limit:model.limit)
                            .frame(width:min(geometry.size.width*0.58,215),height:min(geometry.size.width*0.58,215))
                        Text("Velocidade atual").font(.headline)
                        speedBanner
                        road
                        metrics
                        muteButton
                    }
                    Text("GPS e alertas funcionam sem Internet. A sinalização prevalece.")
                        .font(.caption2).foregroundStyle(.white.opacity(0.55))
                        .multilineTextAlignment(.center)
                        .padding(.top,3)
                }
                .padding(.horizontal,18)
                .padding(.top,12)
                .padding(.bottom,32)
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
        HStack(spacing:12) {
            RadioMark().frame(width:52,height:52)
            VStack(alignment:.leading,spacing:0) {
                Text("PT2VHF").font(.system(size:23,weight:.black,design:.rounded)).italic()
                Text("Speed Alert").font(.system(size:16,weight:.medium))
            }
            Spacer(minLength:6)
            Text("iOS 1.0.6").font(.caption2).foregroundStyle(.gray)
            Button {showSettings=true} label: {
                Image(systemName:"gearshape.fill").font(.system(size:24))
                    .frame(width:48,height:48)
            }.accessibilityLabel("Configurações")
        }
    }
    private var speedBanner:some View {
        HStack(alignment:.firstTextBaseline,spacing:12) {
            Text(model.speed.map {String(Int($0.rounded()))} ?? "—")
                .font(.system(size:91,weight:.bold,design:.rounded))
                .minimumScaleFactor(0.6)
                .contentTransition(.numericText())
            Text("km/h").font(.title2.weight(.bold))
        }
        .foregroundStyle(textTint)
        .frame(maxWidth:.infinity,minHeight:112)
        .background(
            RoundedRectangle(cornerRadius:23)
                .fill(speedTint.gradient)
                .overlay(RoundedRectangle(cornerRadius:23)
                    .stroke((over ? Look.red : speedTint).opacity(0.8),lineWidth:2))
                .shadow(color:over ? Look.red.opacity(flash ? 0.8 : 0.25) : .clear,radius:22)
        )
        .accessibilityLabel("Velocidade atual \(model.speed.map {Int($0.rounded()).description} ?? "desconhecida") quilômetros por hora")
    }
    private var road:some View {
        HStack(spacing:14) {
            Image(systemName:"road.lanes").font(.title).frame(width:36)
            VStack(alignment:.leading,spacing:4) {
                Text(model.roadName).font(.headline).lineLimit(2)
                Text(model.source).font(.caption).foregroundStyle(.gray).lineLimit(2)
            }
            Spacer(minLength:0)
        }
        .frame(maxWidth:.infinity,alignment:.leading)
        .padding(.vertical,6)
    }
    private var metrics:some View {
        HStack(spacing:0) {
            VStack(spacing:5) {
                Image(systemName:"camera.metering.spot").font(.title2)
                Text("Próximo radar").font(.caption2)
                Text(model.radarDistance.map { "\(Int($0.rounded())) m" } ?? "—")
                    .font(.headline).foregroundStyle(model.cameraAlert ? Look.red : .white)
            }.frame(maxWidth:.infinity)
            Divider().overlay(.gray.opacity(0.6)).padding(.vertical,8)
            VStack(spacing:4) {
                SpeedLimitSign(limit:model.limit).frame(width:39,height:39)
                Text("Limite").font(.caption2)
                Text(model.limit.map { "\(Int($0.rounded())) km/h" } ?? "—").font(.subheadline.bold())
            }.frame(maxWidth:.infinity)
            Divider().overlay(.gray.opacity(0.6)).padding(.vertical,8)
            VStack(spacing:5) {
                HStack(alignment:.bottom,spacing:3) {
                    ForEach(0..<4,id:\.self) { i in
                        RoundedRectangle(cornerRadius:2)
                            .fill(i<gpsBars ? .green : .gray.opacity(0.6))
                            .frame(width:6,height:CGFloat(9+i*6))
                    }
                }.frame(height:31,alignment:.bottom)
                Text("GPS").font(.caption2)
                Text(gpsLabel).font(.headline)
            }.frame(maxWidth:.infinity)
        }
        .padding(.vertical,12)
        .background(RoundedRectangle(cornerRadius:20)
            .fill(Look.panel.gradient))
        .overlay(RoundedRectangle(cornerRadius:20)
            .stroke(.gray.opacity(0.18),lineWidth:1))
    }
    private var muteButton:some View {
        Button {model.toggleMute()} label: {
            Label(model.preferences.mute ? "Ativar som" : "Silenciar",
                  systemImage:model.preferences.mute ? "speaker.wave.2.fill" : "speaker.slash.fill")
                .font(.title3.weight(.bold))
                .frame(maxWidth:300,minHeight:54)
                .foregroundStyle(.white)
                .background(Capsule()
                    .fill(Color(red:0.15,green:0.25,blue:0.32).gradient))
                .overlay(Capsule()
                    .stroke(.gray.opacity(0.5),lineWidth:1))
        }.buttonStyle(.plain)
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
    }
}
private struct RadioMark:View {
    var body:some View {
        GeometryReader {g in
            Canvas {ctx,size in
                for (index,factor) in [0.85,0.61,0.36].enumerated() {
                    var path=Path()
                    let r=min(size.width,size.height)*factor*0.5
                    let center=CGPoint(x:size.width/2,y:size.height*0.74)
                    path.addArc(center:center,radius:r,startAngle:.degrees(190),endAngle:.degrees(350),clockwise:false)
                    ctx.stroke(path,with:.color(Look.red),style:StrokeStyle(lineWidth:4.3,lineCap:.round))
                    _=index
                }
            }
        }
    }
}
