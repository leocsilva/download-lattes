package br.unesp.repositorio.downloadlattes.main;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.Normalizer;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

public class Main {

	static final JLabel mensagem = new JLabel("");;

	public static void main(String[] args) throws ClientProtocolException,
			IOException {

		PrintStream log = new PrintStream(new File("download-lattes.log"));
		System.setOut(log);
		File arquivoLista = null;

		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("Arquivo TXT", "txt"));
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fc.setAcceptAllFileFilterUsed(true);
		fc.showDialog(null, "Abrir");
		if (fc.getSelectedFile() != null) {
			arquivoLista = fc.getSelectedFile();
		}
		final Thread esteProcesso = Thread.currentThread();

		new Thread() {
			public void run() {
				JFrame janela = new JFrame();
				janela.setBounds(0, 0, 800, 50);
				janela.setTitle("Download Lattes");
				mensagem.setBounds(0, 0, 800, 50);
				janela.getContentPane().add(mensagem);
				janela.setVisible(true);
				janela.addWindowListener(new WindowAdapter() {

					public void windowClosing(WindowEvent e) {
						esteProcesso.interrupt();
						System.exit(0);

					}

				});
			};
		}.start();

		Scanner lista = new Scanner(arquivoLista);
		while (lista.hasNext())
			try {
				download(lista.next());
			} catch (Exception e) {
				e.printStackTrace();
			}

		lista.close();
		log.flush();
		log.close();
	}

	private static void download(String idcnpq) throws ClientProtocolException,
			IOException {

		String urlRequisicao = String
				.format("http://buscatextual.cnpq.br/buscatextual/download.do?metodo=apresentar&idcnpq=%s",
						idcnpq);
		System.out.println(urlRequisicao);
		
		HttpClient client = HttpClientBuilder.create().build();

		String cookies = getCookie(idcnpq);
		int tentativas = 1;
		boolean catpchaValidado = false;
		System.out.println("Verificando captcha");
		while (!catpchaValidado) {
			mensagem.setText("Tentativa "+tentativas+": idcnpq="+idcnpq);
			Image image = getImage(cookies, urlRequisicao);
			String captcha = resolveCaptchaComOCR(image);
			if (tentativas > 5) {
				captcha = JOptionPane.showInputDialog(
						null,
						"Digite os caracteres da imagem para baixar o arquivo "
								+ idcnpq, "Verifique",
						JOptionPane.INFORMATION_MESSAGE, new ImageIcon(image),
						null, captcha).toString();
			}
			catpchaValidado = validaCaptcha(captcha,cookies,urlRequisicao);
			tentativas++;
		}
		System.out.println("Captcha validado");
		String downloadRequest = "http://buscatextual.cnpq.br/buscatextual/download.do;jsessionid="+cookies.split(";")[0].split("=")[1];
		System.out.println(downloadRequest);
		HttpPost requisicao = new HttpPost(
				downloadRequest);
		requisicao.addHeader("Accept","application/json, text/javascript, */*; q=0.01");
		requisicao.addHeader("Cookie",cookies);
		requisicao.addHeader("Host","buscatextual.cnpq.br");
		requisicao.addHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.81 Safari/537.36");
		requisicao.addHeader("Referer", urlRequisicao);
		requisicao.addHeader("Content-Type",
						"multipart/form-data; boundary=----WebKitFormBoundaryKrQbsUhP7t17ZixP");


		StringEntity entity = new StringEntity(
				String.format(
						"------WebKitFormBoundaryKrQbsUhP7t17ZixP\r\nContent-Disposition: form-data; name=\"metodo\"\r\n\r\ndownloadCV\r\n------WebKitFormBoundaryKrQbsUhP7t17ZixP\r\nContent-Disposition: form-data; name=\"idcnpq\"\r\n\r\n%s\r\n------WebKitFormBoundaryKrQbsUhP7t17ZixP\r\nContent-Disposition: form-data; name=\"informado\"\r\n\r\n\r\n------WebKitFormBoundaryKrQbsUhP7t17ZixP--\r\n",
						idcnpq));

		requisicao.setEntity(entity);
		System.out.println(entity);
		HttpResponse resposta = client.execute(requisicao);
		String ct = resposta.getHeaders("Content-Type")[0].getValue();
		System.out.println(ct);


		int statusCode = resposta.getStatusLine().getStatusCode();
		System.out.println(statusCode+ " - "+resposta.getStatusLine().getReasonPhrase());
		if(statusCode==200){
			salvarZip(idcnpq, resposta.getEntity().getContent());
		}else{
			JOptionPane.showMessageDialog(null, "Erro ao baixar arquivo");
			download(idcnpq);
		}

	}

	private static String resolveCaptchaComOCR(Image image) {
		String ocr = "";

		try {
			

			BufferedImage bi = new BufferedImage(image.getWidth(null),
					image.getHeight(null), BufferedImage.TYPE_INT_RGB);

			Graphics2D g2 = bi.createGraphics();
			g2.drawImage(image, 0, 0, null);
			g2.dispose();
			File img_tmp = new File("temp.jpg");
			ImageIO.write(bi, "jpg", img_tmp);

			Runtime.getRuntime().exec(
					"c:\\Tesseract-OCR\\tesseract.exe temp.jpg out");
			Thread.sleep(2000);
			File out = new File("out.txt");
			Scanner scanner = new Scanner(out);
			String find = scanner.nextLine();
			ocr = Normalizer.normalize(find.replaceAll("[\\p{Punct}]", ""),
					Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("\\s", "");
			scanner.close();
			out.delete();
			img_tmp.delete();
		} catch (Exception e) {
		}
		return ocr;
	}

	private static Image getImage(String cookie, String urlRequisicao) throws ClientProtocolException, IOException {

		String urlImagem = "http://buscatextual.cnpq.br/buscatextual/servlet/captcha?metodo=getImagemCaptcha&noCache="
				+ new java.util.Date().getTime();
		HttpGet get = new HttpGet(urlImagem);
		get.addHeader("Accept","application/json, text/javascript, */*; q=0.01");
		get.addHeader("Cookie",cookie);
		get.addHeader("Host","buscatextual.cnpq.br");
		get.addHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.81 Safari/537.36");
		get.addHeader("Referer", urlRequisicao);
		System.out.println("Baixando imagem de " + urlImagem);
		HttpClient client = HttpClientBuilder.create().build();
		return ImageIO.read(client.execute(get).getEntity().getContent());

	}

	private static String getCookie(String idcnpq)
			throws ClientProtocolException, IOException {
		HttpGet get = new HttpGet(
				String.format(
						"http://buscatextual.cnpq.br/buscatextual/download.do?metodo=apresentar&idcnpq=%s",
						idcnpq));
		
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = client.execute(get);
		
		Header[] cookies = response.getHeaders("Set-Cookie");
		String cookie = "";
		for (Header c : cookies) {
			cookie += c.getValue().split(";")[0]+"; ";
		}
		cookie = cookie.substring(0,cookie.length()-2);
		System.out.println(cookie);
		return cookie;
	}

	private static boolean validaCaptcha(String captcha,String cookie, String urlRequisicao)
			throws ClientProtocolException, IOException {
		if (captcha == null || captcha.trim().isEmpty()) {
			return false;
		}
		String urlValida = "http://buscatextual.cnpq.br/buscatextual/servlet/captcha?"
				+ String.format("informado=%s&metodo=validaCaptcha", captcha);
		System.out.println(urlValida);
		HttpGet get = new HttpGet(urlValida);
		get.addHeader("Accept","application/json, text/javascript, */*; q=0.01");
		get.addHeader("Cookie",cookie);
		get.addHeader("Host","buscatextual.cnpq.br");
		get.addHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.81 Safari/537.36");
		get.addHeader("Referer", urlRequisicao);
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse resposta = client.execute(get);
		int statusCode = resposta.getStatusLine().getStatusCode();
		Scanner sc = new Scanner(resposta.getEntity().getContent());
		System.out.println(statusCode+ " - "+resposta.getStatusLine().getReasonPhrase());
		while(sc.hasNextLine()){
			System.out.println(sc.nextLine());
		}
		sc.close();
		return statusCode == 200;

	}

	private static void salvarZip(String nome, InputStream is){
		try {
             
            OutputStream os = new FileOutputStream(nome+".zip");
             
            byte[] buffer = new byte[1024];
            int bytesRead;
            //read from is to buffer
            while((bytesRead = is.read(buffer)) !=-1){
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            //flush OutputStream to write any buffered data to file
            os.flush();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
