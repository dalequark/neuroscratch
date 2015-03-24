interface EEGLog {
  public int tryConnect() throws Exception;

  public void addUser() throws Exception;

  public double[][] getEEG() throws Exception;

}
