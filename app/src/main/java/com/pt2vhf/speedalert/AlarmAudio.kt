package com.pt2vhf.speedalert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Six synthesized looping sounds, played by the location foreground service.
 * Audio is not bound to the Activity, so navigation apps may stay on screen.
 */
class AlarmAudio(context:Context) {
    private data class Segment(val duration:Int,val hz0:Double,val hz1:Double=hz0)
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes=AudioAttributes.Builder()
        // Navigation guidance (like Waze/Maps): follows media volume and car Bluetooth.
        // SONIFICATION used the system stream, silent on some phones even at full media volume.
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audioRate=22050
    private val focusListener=AudioManager.OnAudioFocusChangeListener { change ->
        Log.d("PT2VHF.Audio","Audio focus update: "+change)
    }
    private var focus:AudioFocusRequest?=null
    private var playback:AudioTrack?=null
    private var currentPattern=""
    private var currentVolume=-1

    private fun segments(style:String,beep:Int,pause:Int):List<Segment> = when(style) {
        "DUPLO" -> listOf(Segment(155,910.0),Segment(105,0.0),
            Segment(155,1050.0),Segment(455,0.0))
        "TRIPLO" -> listOf(Segment(110,980.0),Segment(85,0.0),
            Segment(110,980.0),Segment(85,0.0),Segment(110,980.0),Segment(420,0.0))
        "SIRENE" -> listOf(Segment(460,670.0,1200.0),
            Segment(460,1200.0,670.0))
        "SUAVE" -> listOf(Segment(260,630.0),Segment(480,0.0))
        "CONTINUO" -> listOf(Segment(1000,880.0))
        else -> listOf(Segment(beep.coerceIn(100,1000),870.0),
            Segment(pause.coerceIn(100,2000),0.0))
    }
    private fun pcm(style:String,beep:Int,pause:Int):ByteArray {
        val pattern=segments(style,beep,pause)
        val count=pattern.sumOf {it.duration*audioRate/1000}
        val output=ByteArray(count*2)
        var offset=0
        var phase=0.0
        for(segment in pattern) {
            val len=segment.duration*audioRate/1000
            for(i in 0 until len) {
                val frequency=segment.hz0+(segment.hz1-segment.hz0)*i/len
                val amp=if(segment.hz0==0.0) 0.0 else {
                    phase+=2.0*PI*frequency/audioRate
                    val envelope=if(style=="CONTINUO") 1.0 else
                        min(1.0,min(i,len-1-i)/150.0)
                    sin(phase)*envelope*0.66
                }
                val value=(amp*32767.0).toInt().coerceIn(-32768,32767)
                output[offset++]=(value and 255).toByte()
                output[offset++]=((value ushr 8) and 255).toByte()
            }
        }
        return output
    }

    /** Last start failure, shown by Settings → Testar som (null = playing fine). */
    @Volatile var lastError:String?=null; private set
    @Volatile private var playing=false
    private var thread:Thread?=null

    /**
     * Streams the looping pattern from a worker thread (MODE_STREAM). The static-buffer
     * loop used since 1.0.4 was created and released at once on HyperOS, never playing.
     */
    @Synchronized
    fun setAlert(active:Boolean,style:String,volume:Int,beep:Int,pause:Int) {
        if(!active || volume<=0) {stop();return}
        val key=style+":"+beep+":"+pause
        val level=volume.coerceIn(0,100)
        if(playback!=null && key==currentPattern) {
            if(level!=currentVolume) {
                try { playback?.setVolume(level/100f) } catch(_:Exception) {}
                currentVolume=level
            }
            return
        }
        stop()
        var step="criar"
        try {
            val bytes=pcm(style,beep,pause)
            val format=AudioFormat.Builder()
                .setSampleRate(audioRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val min=AudioTrack.getMinBufferSize(audioRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
            val player=AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min,4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            step="inicializar"
            if(player.state!=AudioTrack.STATE_INITIALIZED) {
                player.release()
                throw IllegalStateException("estado ${player.state}")
            }
            step="foco"
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener,Handler(Looper.getMainLooper()))
                .build()
            manager.requestAudioFocus(request)
            focus=request
            step="tocar"
            player.setVolume(level/100f)
            player.play()
            playback=player
            currentPattern=key
            currentVolume=level
            playing=true
            thread=Thread {
                try { while(playing) { if(player.write(bytes,0,bytes.size)<0)break } }
                catch(e:Exception) { Log.e("PT2VHF.Audio","write",e) }
                finally { try { player.stop() } catch(_:Exception) {}; player.release() }
            }.apply { name="pt2vhf-alarm";start() }
            lastError=null
        }catch(e:Exception) {
            lastError="falha ao $step: "+(e.message?:e.javaClass.simpleName)
            Log.e("PT2VHF.Audio","Could not start alarm ($step)",e)
            stop()
        }
    }
    @Synchronized
    fun stop() {
        playing=false
        // stop() makes the blocked write() return; the worker thread then releases the track.
        try { playback?.stop() } catch(_:Exception){}
        playback=null
        thread=null
        focus?.let {manager.abandonAudioFocusRequest(it)}
        focus=null
        currentPattern=""
        currentVolume=-1
    }
    fun close()=stop()
}
