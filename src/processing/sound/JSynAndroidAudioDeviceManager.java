package processing.sound;

import java.util.ArrayList;

import com.jsyn.devices.AudioDeviceInputStream;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.devices.AudioDeviceOutputStream;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioRecord;
import android.media.MediaRecorder;

class JSynAndroidAudioDeviceManager implements AudioDeviceManager {

	ArrayList<DeviceInfo> deviceRecords;
	private double suggestedOutputLatency = 0.1;
	private double suggestedInputLatency = 0.1;
	private int defaultInputDeviceID = 0;
	private int defaultOutputDeviceID = 0;

	public JSynAndroidAudioDeviceManager() {
		this.deviceRecords = new ArrayList<DeviceInfo>();
		DeviceInfo deviceInfo = new DeviceInfo();

		deviceInfo.name = "Android Audio";
		deviceInfo.maxInputs = 1;
		deviceInfo.maxOutputs = 2;
		this.deviceRecords.add(deviceInfo);
	}

	public String getName() {
		return "JSyn Android Audio for Processing";
	}

	class DeviceInfo {
		String name;
		int maxInputs;
		int maxOutputs;

		public String toString() {
			return "AudioDevice: " + name + ", max in = " + maxInputs + ", max out = " + maxOutputs;
		}
	}

	private class AndroidAudioStream {
		short[] shortBuffer;
		int frameRate;
		int samplesPerFrame;
		int minBufferSize;
		int bufferSize;

		public AndroidAudioStream(int deviceID, int frameRate, int samplesPerFrame) {
			this.frameRate = frameRate;
			this.samplesPerFrame = samplesPerFrame;
		}

		public double getLatency() {
			int numFrames = this.bufferSize / this.samplesPerFrame;
			return ((double) numFrames) / this.frameRate;
		}

	}

	private class AndroidAudioOutputStream extends AndroidAudioStream implements AudioDeviceOutputStream {
		AudioTrack audioTrack;

		public AndroidAudioOutputStream(int deviceID, int frameRate, int samplesPerFrame) {
			super(deviceID, frameRate, samplesPerFrame);
		}

		public void start() {
			this.minBufferSize = AudioTrack.getMinBufferSize(this.frameRate, AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT);
			this.bufferSize = (3 * (this.minBufferSize / 2)) & ~3;
			this.audioTrack = new AudioTrack.Builder()
					.setAudioAttributes(new AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
					.setAudioFormat(new AudioFormat.Builder()
							.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(this.frameRate)
							.build())
					.setBufferSizeInBytes(this.bufferSize)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.build();
			this.audioTrack.play();
		}

		public void write(double value) {
			double[] buffer = new double[1];
			buffer[0] = value;
			this.write(buffer, 0, 1);
		}

		public void write(double[] buffer) {
			this.write(buffer, 0, buffer.length);
		}

		public void write(double[] buffer, int start, int count) {
			if ((this.shortBuffer == null) || (this.shortBuffer.length < count)) {
				this.shortBuffer = new short[count];
			}

			for (int i = 0; i < count; i++) {
				int sample = (int) (32767.0 * buffer[i + start]);
				if (sample > Short.MAX_VALUE) {
					sample = Short.MAX_VALUE;
				} else if (sample < Short.MIN_VALUE) {
					sample = Short.MIN_VALUE;
				}
				this.shortBuffer[i] = (short) sample;
			}

			this.audioTrack.write(this.shortBuffer, 0, count);
		}

		public void stop() {
			if (this.audioTrack != null) {
				this.audioTrack.stop();
				this.audioTrack.release();
			}
		}

		public void close() {
		}

	}

	private class AndroidAudioInputStream extends AndroidAudioStream implements AudioDeviceInputStream {
		AudioRecord audioRecord;

		public AndroidAudioInputStream(int deviceID, int frameRate, int samplesPerFrame) {
			super(deviceID, frameRate, samplesPerFrame);
		}

		public void start() {
			this.minBufferSize = AudioRecord.getMinBufferSize(this.frameRate, AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT);
			this.bufferSize = (3 * (this.minBufferSize / 2)) & ~3;
			this.audioRecord = new AudioRecord.Builder()
					.setAudioSource(MediaRecorder.AudioSource.MIC)
					.setAudioFormat(new AudioFormat.Builder()
							.setChannelMask(AudioFormat.CHANNEL_IN_MONO)
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(this.frameRate)
							.build())
					.setBufferSizeInBytes(this.bufferSize)
					.build();
			this.audioRecord.startRecording();
		}

		public double read() {
			double[] buffer = new double[1];
			this.read(buffer, 0, 1);
			return buffer[0];
		}

		public int read(double[] buffer) {
			return this.read(buffer, 0, buffer.length);
		}

		public int read(double[] buffer, int start, int count) {
			if ((this.shortBuffer == null) || (this.shortBuffer.length < count)) {
				this.shortBuffer = new short[count];
			}

			int read = this.audioRecord.read(this.shortBuffer, 0, count, AudioRecord.READ_NON_BLOCKING);

			if (read < 0) {
				switch (read) {
				case AudioRecord.ERROR_INVALID_OPERATION:
					throw new RuntimeException("AudioRecord ERROR_INVALID_OPERATION: Device not properly initialized");
				case AudioRecord.ERROR_BAD_VALUE:
					throw new RuntimeException("AudioRecord ERROR_BAD_VALUE: Paramters don't resolve to valid data and indices");
				case AudioRecord.ERROR_DEAD_OBJECT:
					throw new RuntimeException("AudioRecord ERROR_DEAD_OBJECT: Object must be recreated");
				case AudioRecord.ERROR:
					throw new RuntimeException("AudioRecord ERROR: Unknown error");
				}
			}

			for (int i = 0; i < read; i++) {
				buffer[i + start] = shortBuffer[i] / 32767.0;
			}

			return read;
		}

		public void stop() {
			if (this.audioRecord != null) {
				this.audioRecord.stop();
				this.audioRecord.release();
			}
		}

		public int available() {
			return this.bufferSize;
		}

		public void close() {
		}
	}

	public AudioDeviceOutputStream createOutputStream(int deviceID, int frameRate, int samplesPerFrame) {
		return new AndroidAudioOutputStream(deviceID, frameRate, samplesPerFrame);
	}

	public AudioDeviceInputStream createInputStream(int deviceID, int frameRate, int samplesPerFrame) {
		return new AndroidAudioInputStream(deviceID, frameRate, samplesPerFrame);
	}

	public double getDefaultHighInputLatency(int deviceID) {
		return 0.3;
	}

	public double getDefaultHighOutputLatency(int deviceID) {
		return 0.3;
	}

	public int getDefaultInputDeviceID() {
		return this.defaultInputDeviceID;
	}

	public int getDefaultOutputDeviceID() {
		return this.defaultOutputDeviceID;
	}

	public double getDefaultLowInputLatency(int deviceID) {
		return 0.1;
	}

	public double getDefaultLowOutputLatency(int deviceID) {
		return 0.1;
	}

	public int getDeviceCount() {
		return this.deviceRecords.size();
	}

	public String getDeviceName(int deviceID) {
		return this.deviceRecords.get(deviceID).name;
	}

	public int getMaxInputChannels(int deviceID) {
		return this.deviceRecords.get(deviceID).maxInputs;
	}

	public int getMaxOutputChannels(int deviceID) {
		return this.deviceRecords.get(deviceID).maxOutputs;
	}

	public int setSuggestedOutputLatency(double latency) {
		this.suggestedOutputLatency = latency;
		return 0;
	}

	public int setSuggestedInputLatency(double latency) {
		this.suggestedInputLatency = latency;
		return 0;
	}

}
