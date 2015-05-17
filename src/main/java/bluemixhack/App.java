package bluemixhack;

import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_NONE;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_YUV420P;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.apache.commons.codec.binary.Base64;
import java.util.List;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder.Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.BufferedImageHttpMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@SpringBootApplication
@RestController
public class App {
	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	private static final Logger log = LoggerFactory.getLogger(App.class); // 後で使う

	boolean isRecording = false;
	List<BufferedImage> list = new ArrayList<BufferedImage>();

	@Bean
	// HTTPのリクエスト・レスポンスボディにBufferedImageを使えるようにする
	BufferedImageHttpMessageConverter bufferedImageHttpMessageConverter() {
		return new BufferedImageHttpMessageConverter();
	}

	@Configuration
	@EnableWebSocketMessageBroker
	// WebSocketに関する設定クラス
	static class StompConfig extends AbstractWebSocketMessageBrokerConfigurer {

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("endpoint"); // WebSocketのエンドポイント
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.setApplicationDestinationPrefixes("/app"); // Controllerに処理させる宛先のPrefix
		}

		@Override
		public void configureWebSocketTransport(
				WebSocketTransportRegistration registration) {
			registration.setMessageSizeLimit(10 * 1024 * 1024); // メッセージサイズの上限を10MBに上げる(デフォルトは64KB)
		}
	}

	/**
	 * @param base64Image
	 */
	@MessageMapping(value = "/cameraRecorder")
	void cameraRecorder(String base64Image) {
		if (isRecording) {
			byte[] inTestStrByte = base64Image.getBytes();  
		      
		    // 文字列をBase64にエンコード  
//		    byte[] outTestStrBtye = Base64.encodeBase64(inTestStrByte);
			Message<byte[]> message = MessageBuilder.withPayload(
					Base64.decodeBase64(inTestStrByte)).build();

			try (InputStream stream = new ByteArrayInputStream(
					message.getPayload())) {
				BufferedImage image = ImageIO.read(stream);
				list.add(image);
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (list.size() == 60) {
				isRecording = false;
				FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
						"/tmp/livemovie.mp4", 400, 300);
				recorder.setVideoCodec(AV_CODEC_ID_NONE);
				recorder.setFrameRate(10);
				recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
				try {
					recorder.start();
					for (BufferedImage image : list) {
						recorder.record(IplImage.createFrom(image));
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try {
						recorder.stop();
						recorder.release();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					list.clear();
				}
			}
		}
	}

	@RequestMapping(value = "/shoot")
	public String shoot() {
		isRecording = true;
		return "OK";
	}

	@RequestMapping(value = "/download")
	public String doDownload(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		File file = new File("/tmp/livemovie.mp4");
		byte[] byteArray = FileUtils.readFileToByteArray(file);
		response.setContentType("application/octet-stream");
		response.setContentLength(byteArray.length);
		response.setHeader("Content-disposition", "attachment; filename=\""
				+ file.getName() + "\"");
		FileCopyUtils.copy(byteArray, response.getOutputStream());

		return "";
	}
}
