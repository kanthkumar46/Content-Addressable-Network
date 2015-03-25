public enum Requests {
	JOIN(1),
	UPDATE_VID(2),
	REMOVE_VID(3), 
	FORWARD_JOIN(4), 
	TRANFER_CONTROL(5),
	TAKE_OVER(6);
	
	int request;
	
	private Requests(int request) {
		this.request = request;
	}
}
