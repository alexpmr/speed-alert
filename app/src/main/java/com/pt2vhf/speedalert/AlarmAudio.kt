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
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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

    @Synchronized
    fun setAlert(active:Boolean,style:String,volume:Int,beep:Int,pause:Int) {
        if(!active || volume<=0) {stop();return}
        val key=style+":"+beep+":"+pause
        val level=volume.coerceIn(0,100)
        if(playback!=null && key==currentPattern) {
            if(level!=currentVolume) {
                playback?.setVolume(level/100f)
                currentVolume=level
            }
            return
        }
        stop()
        try {
            val bytes=pcm(style,beep,pause)
            val format=AudioFormat.Builder()
                .setSampleRate(audioRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val player=AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            if(player.state!=AudioTrack.STATE_INITIALIZED) {
                player.release()
                throw IllegalStateException("AudioTrack initialization failed")
            }
            if(player.write(bytes,0,bytes.size)!=bytes.size) {
                player.release()
                throw IllegalStateException("Could not write looping alarm")
            }
            if(player.setLoopPoints(0,bytes.size/2,-1)!=AudioTrack.SUCCESS) {
                player.release()
                throw IllegalStateException("Could not configure looping alarm")
            }
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener,Handler(Looper.getMainLooper()))
                .build()
            manager.requestAudioFocus(request)
            focus=request
            playback=player
            currentPattern=key
            currentVolume=level
            player.setVolume(level/100f)
            player.play()
        }catch(e:Exception) {
            Log.e("PT2VHF.Audio","Could not start background alarm",e)
            stop()
        }
    }
    /**
     * Short two-tone chime (radar ahead). Never interrupts a looping alarm:
     * if one is already playing, the chime is skipped.
     */
    @Synchronized
    fun chime(volume:Int) {
        if(playback!=null || volume<=0)return
        try {
            val tones=listOf(Segment(170,1320.0),Segment(40,0.0),Segment(260,990.0))
            val count=tones.sumOf {it.duration*audioRate/1000}
            val bytes=ByteArray(count*2)
            var offset=0
            var phase=0.0
            for(segment in tones) {
                val len=segment.duration*audioRate/1000
                for(i in 0 until len) {
                    val amp=if(segment.hz0==0.0)0.0 else {
                        phase+=2.0*PI*segment.hz0/audioRate
                        sin(phase)*min(1.0,min(i,len-1-i)/150.0)*0.66
                    }
                    val value=(amp*32767.0).toInt().coerceIn(-32768,32767)
                    bytes[offset++]=(value and 255).toByte()
                    bytes[offset++]=((value ushr 8) and 255).toByte()
                }
            }
            val player=AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(audioRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(bytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            if(player.state!=AudioTrack.STATE_INITIALIZED || player.write(bytes,0,bytes.size)!=bytes.size) {
                player.release();return
            }
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes).build()
            manager.requestAudioFocus(request)
            player.setVolume(volume.coerceIn(0,100)/100f)
            player.play()
            Handler(Looper.getMainLooper()).postDelayed({
                try { player.stop() } catch(_:Exception) {}
                player.release()
                manager.abandonAudioFocusRequest(request)
            },count*1000L/audioRate+150L)
        }catch(e:Exception) {
            Log.e("PT2VHF.Audio","Could not play radar chime",e)
        }
    }
    @Synchronized
    fun stop() {
        try {
            playback?.pause()
            playback?.flush()
            playback?.release()
        }catch(_:Exception){}
        playback=null
        focus?.let {manager.abandonAudioFocusRequest(it)}
        focus=null
        currentPattern=""
        currentVolume=-1
    }
    fun close()=stop()
}
