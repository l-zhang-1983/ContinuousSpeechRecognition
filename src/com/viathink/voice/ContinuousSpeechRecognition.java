package com.viathink.voice;

import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.TarsosDSPAudioFloatConverter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

import com.iflytek.cloud.speech.RecognizerListener;
import com.iflytek.cloud.speech.RecognizerResult;
import com.iflytek.cloud.speech.Setting;
import com.iflytek.cloud.speech.SpeechConstant;
import com.iflytek.cloud.speech.SpeechError;
import com.iflytek.cloud.speech.SpeechRecognizer;
import com.iflytek.cloud.speech.SpeechUtility;

public class ContinuousSpeechRecognition {
    private final String appId = "573975b6";
    private String inputFilePath;
    private String outputFilePath;
    private OutputStreamWriter osw = null;
    private final int readLength = 4800;
    private int partTime = 10 * 1000;
    private ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<byte[]>();
    private boolean isRunning = false; // 识别引擎是否在执行识别
    private boolean isFinishReadFile = false; // 是否读取完文件
    private SpeechRecognizer speechRecognizer;
    private StringBuffer recognizerResult = new StringBuffer();

    public ContinuousSpeechRecognition(String inputFilePath, String outputFilePath) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
        Setting.setShowLog(false);
        SpeechUtility.createUtility("appid=" + this.appId);
        SpeechRecognizer.createRecognizer();
        speechRecognizer = SpeechRecognizer.getRecognizer();
        speechRecognizer.setParameter(SpeechConstant.DOMAIN, "iat");
        speechRecognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        speechRecognizer.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
        speechRecognizer.setParameter(SpeechConstant.RESULT_TYPE, "plain");
        speechRecognizer.setParameter(SpeechConstant.VAD_BOS, "10000");
        speechRecognizer.setParameter(SpeechConstant.VAD_EOS, "10000");
    }

    public ContinuousSpeechRecognition(String inputFilePath, String outputFilePath, int partTime) {
        this(inputFilePath, outputFilePath);
        this.partTime = partTime;
    }

    public void start() {
        File audioFile = new File(this.inputFilePath);
        FileInputStream fis;
        try {
            audioQueue.clear();
            setIsRunning(true);
            isFinishReadFile = false;
            speechRecognizer.startListening(recListener);
            Thread sttThread = new Thread(runnable);
            sttThread.start();
            fis = new FileInputStream(audioFile);
            osw = new OutputStreamWriter(new FileOutputStream(outputFilePath));
            byte[] byteArr = new byte[this.readLength];
            int size = 0;
            while ((size = fis.read(byteArr)) != -1) {
                audioQueue.add(byteArr.clone());
            }

            while (!audioQueue.isEmpty()) {
                // DebugLog.Log("队列还有内容,等待读取,延迟结束...");
                Thread.sleep(2000);
            }
            isFinishReadFile = true;
            if (fis != null) {
                fis.close();
            }
            while (sttThread.isAlive()) {
                // DebugLog.Log("等待识别线程结束...");
                Thread.sleep(2000);
            }
            speechRecognizer.destroy();
            DebugLog.Log("全部结束....");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int currentPartTime = 0;
            while (!isFinishReadFile) {// 条件是主动结束,并且队列中已经没有数据
                if (getIsRunning()) {
                    // 取出byte[]
                    byte[] data = audioQueue.poll();
                    if (data == null) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                    // 去检验静音
                    TarsosDSPAudioFormat tdspFormat = new TarsosDSPAudioFormat(
                            16000, 16, 1, true, false);
                    float[] voiceFloatArr = new float[readLength
                            / tdspFormat.getFrameSize()];
                    TarsosDSPAudioFloatConverter audioFloatConverter = TarsosDSPAudioFloatConverter
                            .getConverter(tdspFormat);
                    audioFloatConverter.toFloatArray(data.clone(),
                            voiceFloatArr);
                    SilenceDetector silenceDetector = new SilenceDetector();
                    boolean isSlience = silenceDetector
                            .isSilence(voiceFloatArr);
                    try {

                        // 如果是静音,其实这个地方可以再优化,比如连续读到3或4个静音才算静音端点
                        if (currentPartTime >= partTime) {
                            if (isSlience) {
                                // 如果是静音端点,重新启动
                                // DebugLog.Log("检测到端点..等待识别完成后重新启动..");
                                speechRecognizer.stopListening();
                                setIsRunning(false);
                                currentPartTime = 0;
                            } else {
                                currentPartTime = currentPartTime + 150;
                                speechRecognizer.writeAudio(data, 0, data.length);
                                Thread.sleep(1);
                            }
                        } else {
                            /*
                             * +150是4800字节相当于150毫秒.这里不需要太准确,因为我们在分片时间后开始寻找静音端点,
                             * 寻找到端点就调用stopListening,强制识别
                             */
                            currentPartTime = currentPartTime + 150;
                            speechRecognizer.writeAudio(data, 0, data.length);
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            speechRecognizer.stopListening();

            if (osw != null) {
                try {
                    osw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            DebugLog.Log("退出.....");
        }
    };
    /**
     * 听写监听器
     */
    private RecognizerListener recListener = new RecognizerListener() {

        public void onBeginOfSpeech() {
            DebugLog.Log("*************开始录音*************");
        }

        public void onEndOfSpeech() {
            DebugLog.Log("************结束录音事件**************");
        }

        public void onVolumeChanged(int volume) {
            /*
             * DebugLog.Log( "onVolumeChanged enter" ); if (volume > 0)
             * DebugLog.Log("*************音量值:" + volume + "*************");
             */
        }

        public void onResult(RecognizerResult result, boolean islast) {
            recognizerResult.append(result.getResultString());
            if (islast) {
                DebugLog.Log("识别结果为:" + recognizerResult.toString());
                try {
                    osw.write(recognizerResult.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                recognizerResult.delete(0, recognizerResult.length());
                speechRecognizer.startListening(recListener);
                setIsRunning(true);
            }
        }

        public void onError(SpeechError error) {
            if (error.getErrorCode() == 10118) {
                // 10118是未检测到说话,重新开始一次
                DebugLog.Log("error: 未检测到说话,重新启动..");
                speechRecognizer.startListening(recListener);
                setIsRunning(true);
            } else {
                DebugLog.Log("其他错误------------: " + error.getErrorCode());
            }
        }

        public void onEvent(int eventType, int arg1, int agr2, String msg) {
        }

    };

    private synchronized void setIsRunning(boolean running) {
        isRunning = running;
    }

    private synchronized boolean getIsRunning() {
        return isRunning;
    }
}
