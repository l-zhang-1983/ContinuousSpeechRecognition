package com.viathink.main;

import com.viathink.voice.ContinuousSpeechRecognition;

public class Main {

    public static void main(String[] args) {
        /**
         * 1.构造函数第一个参数是文件路径ljsw.wav是逻辑思维50分钟的录音，由于文件太大就没有上传到github，
         * 可以自己根据readme中关于ffmpeg的介绍自己生成一个pcm编码格式的wav，放在项目中。
         * 2.构造函数第二个参数指:多长时间后开始寻找静音端点。
         */
        ContinuousSpeechRecognition csr = new ContinuousSpeechRecognition(
                "all.wav",
                "result.txt",
                30 * 1000
        );
        csr.start();
    }

}
