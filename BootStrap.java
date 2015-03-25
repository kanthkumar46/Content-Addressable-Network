

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BootStrap{
	ServerSocket serverSoc = null;
	private List<String> CANNodes = new ArrayList<>();
	
	public BootStrap(int port) {
		try {
			serverSoc = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public synchronized String getEntryPoint(){
		Random random = new Random();
		String ipAddress ;
		if(CANNodes.size()>0){
			int index = random.nextInt(CANNodes.size());
			ipAddress = CANNodes.get(index);
		}
		else{
			ipAddress = "999";
		}
		
		return ipAddress;
	}
	
	public synchronized void UpdateCANNodes(String ipAddress, int Signal){
		if(Signal == 1)
			CANNodes.add(ipAddress);
		else
			CANNodes.remove(ipAddress);
	}
	
	public static void main(String[] args) throws IOException {
		if(args.length > 1)
			System.out.println("invalid arguments");
		else 
			new BootStrap(8000).startBootStrap();
	}

	private void startBootStrap() {
		Socket clientSoc;
		while(true){
			try {
				clientSoc = serverSoc.accept();
				new clientHandler(clientSoc, this).start();
			} catch (IOException e) {
				System.out.println("Cannot start BootStrap server :"+e.getMessage());
			}
		}
	}
}


class clientHandler extends Thread{
	Socket clientSoc;
	BootStrap server;
	
	public clientHandler(Socket socket, BootStrap bootStrap) {
		clientSoc = socket;
		server = bootStrap;
	}
	
	@Override
	public void run() {
		try {
			BufferedReader in = new BufferedReader
					(new InputStreamReader (clientSoc.getInputStream()));
			PrintWriter out = new PrintWriter(clientSoc.getOutputStream(),true);
			
			out.println(server.getEntryPoint());
			
			String clientSignal = null;
			while((clientSignal = in.readLine())!=null){
				server.UpdateCANNodes(in.readLine(),Integer.parseInt(clientSignal));
				if(clientSignal.equals("1"))
					out.println("Joined CAN System Successfully");
				else
					out.println("Left CAN System Successfully");
				break;
			}
			
			in.close();
			out.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
}