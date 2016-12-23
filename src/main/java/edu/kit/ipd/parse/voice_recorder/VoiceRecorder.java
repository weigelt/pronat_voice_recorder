package edu.kit.ipd.parse.voice_recorder;

/*
*
* Copyright (c) 1999 Sun Microsystems, Inc. All Rights Reserved.
*
* Sun grants you ("Licensee") a non-exclusive, royalty free,
* license to use, modify and redistribute this software in
* source and binary code form, provided that i) this copyright
* notice and license appear on all copies of the software; and
* ii) Licensee does not utilize the software in a manner
* which is disparaging to Sun.
*
* This software is provided "AS IS," without a warranty
* of any kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS
* AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE
* HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR
* ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING
* OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT
* WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT
* OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
* INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS
* OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY
* TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY
* OF SUCH DAMAGES.

This software is not designed or intended for use in on-line
control of aircraft, air traffic, aircraft navigation or
aircraft communications; or in the design, construction,
operation or maintenance of any nuclear facility. Licensee
represents and warrants that it will not use or redistribute
the Software for such purposes.
*/

/*  The above copyright statement is included because this
* program uses several methods from the JavaSoundDemo
* distributed by SUN. In some cases, the sound processing methods
* unmodified or only slightly modified.
* All other methods copyright Steve Potts, 2002
*/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.SoftBevelBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.ipd.parse.luna.tools.ConfigManager;

/**
 * SimpleSoundCapture Example. This is a simple program to record sounds and
 * play them back. It uses some methods from the CapturePlayback program in the
 * JavaSoundDemo. For licensizing reasons the disclaimer above is included.
 *
 * @author Steve Potts
 */
public class VoiceRecorder extends JPanel implements ActionListener {

	private static final Logger logger = LoggerFactory.getLogger(VoiceRecorder.class);

	String userDirectory = System.getProperty("user.home");

	Properties props = ConfigManager.getConfiguration(getClass());

	String targetDirectory = userDirectory + props.getProperty("PATH");

	String path = "";

	int resolution = Integer.parseInt(props.getProperty("RESOLUTION"));

	final int bufSize = 16384;

	Capture capture = new Capture();

	AudioInputStream audioInputStream;

	JButton captB;

	JTextField textField;

	String errStr;

	double duration, seconds;

	File file;

	public VoiceRecorder() {
		setLayout(new BorderLayout());
		final EmptyBorder eb = new EmptyBorder(5, 5, 5, 5);
		final SoftBevelBorder sbb = new SoftBevelBorder(SoftBevelBorder.LOWERED);
		setBorder(new EmptyBorder(5, 5, 5, 5));

		final JPanel p1 = new JPanel();
		p1.setLayout(new BoxLayout(p1, BoxLayout.X_AXIS));

		final JPanel p2 = new JPanel();
		p2.setBorder(sbb);
		p2.setLayout(new BoxLayout(p2, BoxLayout.Y_AXIS));

		final JPanel buttonsPanel = new JPanel();
		buttonsPanel.setBorder(new EmptyBorder(10, 0, 5, 0));
		captB = addButton("Record", buttonsPanel, true);
		p2.add(buttonsPanel);

		p1.add(p2);
		add(p1);
	}

	public void open() {
	}

	public void close() {
		if (capture.thread != null) {
			captB.doClick(0);
		}
	}

	private JButton addButton(String name, JPanel p, boolean state) {
		final JButton b = new JButton(name);
		b.addActionListener(this);
		b.setEnabled(state);
		p.add(b);
		return b;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		final Object obj = e.getSource();
		if (obj.equals(captB)) {
			if (captB.getText().startsWith("Record")) {
				capture.start();
				captB.setText("Stop");
			} else {
				capture.stop();
			}

		}
	}

	/**
	 * Reads data from the input channel and writes to the output stream
	 */
	class Capture implements Runnable {

		TargetDataLine line;

		Thread thread;

		public void start() {
			errStr = null;
			thread = new Thread(this);
			thread.setName("Capture");
			thread.start();
		}

		public void stop() {
			thread = null;
		}

		private void shutDown(String message) {
			if ((errStr = message) != null && thread != null) {
				thread = null;
				captB.setText("Record");
				System.err.println(errStr);
			}
		}

		@Override
		public void run() {

			duration = 0;
			audioInputStream = null;

			// define the required attributes for our line,
			// and make sure a compatible line is supported.

			final AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
			final float rate = 44100.0f;
			final int channels = 2;
			final int sampleSize = resolution; // 16 means 16 Bit resolution
			final boolean bigEndian = false;

			final AudioFormat format = new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize / 8) * channels, rate, bigEndian);

			final DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

			if (!AudioSystem.isLineSupported(info)) {
				shutDown("Line matching " + info + " not supported.");
				return;
			}

			// get and open the target data line for capture.

			try {
				line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format, line.getBufferSize());
			} catch (final LineUnavailableException ex) {
				shutDown("Unable to open the line: " + ex);
				return;
			} catch (final SecurityException ex) {
				shutDown(ex.toString());
				// JavaSound.showInfoDialog();
				return;
			} catch (final Exception ex) {
				shutDown(ex.toString());
				return;
			}

			// play back the captured audio data
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final int frameSizeInBytes = format.getFrameSize();
			final int bufferLengthInFrames = line.getBufferSize() / 8;
			final int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			final byte[] data = new byte[bufferLengthInBytes];
			int numBytesRead;

			line.start();

			while (thread != null) {
				if ((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
					break;
				}
				out.write(data, 0, numBytesRead);
			}

			// we reached the end of the stream.
			// stop and close the line.
			line.stop();
			line.close();
			line = null;

			// stop and close the output stream
			try {
				out.flush();
				out.close();
			} catch (final IOException ex) {
				ex.printStackTrace();
			}

			// load bytes into the audio input stream for playback

			final byte audioBytes[] = out.toByteArray();
			final ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
			audioInputStream = new AudioInputStream(bais, format, audioBytes.length / frameSizeInBytes);

			final AudioInputStream playbackInputStream = AudioSystem.getAudioInputStream(format, audioInputStream);

			// checks if the target directory already exists, if not it will be
			// created or if not possible an error is thrown.
			final File checkPath = new File(targetDirectory);
			if (checkPath.exists()) {
				;
			} else if (checkPath.mkdirs()) {
				;
				// directory was created.
			} else {
				logger.error(
						"The target directory does not exist and could not be created. Make sure you have the rights to write into this directory: "
								+ targetDirectory);
			}

			String timestamp = new java.util.Date().toString();
			timestamp = timestamp.replace(" ", "_");
			path = targetDirectory + "voiceRecord-" + timestamp + ".flac";
			try {
				final File outputFile = new File(path);
				logger.info("Voice record is saved under: " + path);
				final FLAC_FileEncoder fe = new FLAC_FileEncoder();
				fe.encode(playbackInputStream, outputFile);
			} catch (final Exception e) {
				logger.error("The record could not be saved under: " + path);
			}

			final long milliseconds = (long) ((audioInputStream.getFrameLength() * 1000) / format.getFrameRate());
			duration = milliseconds / 1000.0;

			try {
				audioInputStream.reset();
			} catch (final Exception ex) {
				ex.printStackTrace();
				return;
			}

		}
	} // End class Capture

	public static void main(String s[]) {
		final VoiceRecorder vc = new VoiceRecorder();
		vc.open();
		final JFrame f = new JFrame("Capture/Playback");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.getContentPane().add("Center", vc);
		f.pack();
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final int w = 360;
		final int h = 170;
		f.setLocation(screenSize.width / 2 - w / 2, screenSize.height / 2 - h / 2);
		f.setSize(w, h);
		f.setVisible(true);
	}
}