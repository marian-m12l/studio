package studio;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.io.FileUtils;
import org.usb4java.Device;

import studio.database.JsonPack;
import studio.database.Library;
import studio.driver.event.DeviceHotplugEventListener;
import studio.driver.event.TransferProgressListener;
import studio.driver.fs.FsStoryTellerAsyncDriver;
import studio.driver.model.TransferStatus;
import studio.driver.model.fs.FsDeviceInfos;
import studio.driver.model.fs.FsStoryPackInfos;
import studio.driver.model.raw.RawDeviceInfos;
import studio.driver.model.raw.RawStoryPackInfos;
import studio.driver.raw.RawStoryTellerAsyncDriver;
import java.awt.Toolkit;


public class GUI {

//	public class StoryPackInfosAndMetadata {
//		protected FsStoryPackInfos pack;
//		protected StoryPackMetadata metadata;
//		
//		protected StoryPackInfosAndMetadata(FsStoryPackInfos pack, StoryPackMetadata metadata) {
//			this.pack = pack;
//			this.metadata = metadata;
//		}
//		
//	}
	
	private JFrame frmLuniiTransfer;
	private JTextField serialNumberTextBox;
	private JTextField uUIDTextBox;
	private JTextField firmwareTextBox;
	private JTextField totalSizeTextBox;
	private JTextField usedSizeTextBox;
	
	private FsStoryTellerAsyncDriver fsDriver;
	private RawStoryTellerAsyncDriver rawDriver;
	private JList<JsonPack> devicePacksList;
	private JList<JsonPack> libraryPacksList;
	private Library library;
	private DefaultListModel<JsonPack> libraryPacksModel;
	private DefaultListModel<JsonPack> devicePacksModel;
	
	private boolean fsDriverUsed = false;
	private boolean rawDriverUsed = false;
	private JProgressBar installUninstallProgressBar;
	private JProgressBar globalProgressBar;
	private JLabel devicePacksSummaryLabel;
	private JLabel libraryPacksSummaryLabel;
	private JButton btnValidate;
	private JButton btnRefresh;
	private JButton btnDownloadPackages;
	private JButton btnRefreshLibrary;
	private ResourceBundle localization;
	private Label lblTransferingPack;
	
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(
				            UIManager.getSystemLookAndFeelClassName());

					
					
					GUI window = new GUI();
					window.frmLuniiTransfer.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public GUI() {
		localization = ResourceBundle.getBundle("studio/localization/Translation", Locale.getDefault());
		
		initialize();
		initializeLunii();
		
	}
	
	private CompletableFuture<List<JsonPack>> loadPacksFromDevice() {
		if ( fsDriverUsed  ) {
			return fsDriver.getPacksList().thenComposeAsync( (List<FsStoryPackInfos> packs) -> {
				return library.fetchFromServer().thenApplyAsync( (Void v) -> {
					List<JsonPack> res = packs.stream()
							.map( pack -> library.getPackForUUID( pack.getUuid().toString())) 
							.filter( Optional::isPresent )
							.map( Optional::get )
							.toList();
					return res;
				});
			});
		} else if ( rawDriverUsed ){
			return rawDriver.getPacksList().thenComposeAsync( (List<RawStoryPackInfos> packs) -> {
				return library.fetchFromServer().thenApplyAsync( (Void v) -> {
					List<JsonPack> res = packs.stream()
							.map( pack -> library.getPackForUUID( pack.getUuid().toString())) 
							.filter( Optional::isPresent )
							.map( Optional::get )
							.toList();
					return res;
				});
			});		
		} else {
			return CompletableFuture.supplyAsync(() -> new ArrayList<JsonPack>());
		}
	}
	
	private void initializeLunii() {
		
		library = Library.getInstance(System.getProperty("user.home"));
		
		library.getPacks().thenAcceptAsync( (List<JsonPack> packs) -> {
			libraryPacksModel.addAll(packs);
			btnRefreshLibrary.setEnabled(true);
		});


		fsDriver = new FsStoryTellerAsyncDriver();
		rawDriver = new RawStoryTellerAsyncDriver();
		
		fsDriver.registerDeviceListener(new DeviceHotplugEventListener() {
			
			@Override
			public void onDeviceUnplugged(Device device) {
				devicePacksModel.clear();
				serialNumberTextBox.setText("");
				uUIDTextBox.setText("");
				firmwareTextBox.setText("");
				totalSizeTextBox.setText("");
				usedSizeTextBox.setText("");
				
				btnDownloadPackages.setEnabled(false);
				btnValidate.setEnabled(false);
				btnRefresh.setEnabled(false);
			}
			
			@Override
			public void onDevicePlugged(Device device) {
				FsDeviceInfos deviceInfos;
				try {
					deviceInfos = fsDriver.getDeviceInfos().get();
					serialNumberTextBox.setText(deviceInfos.getSerialNumber());
					uUIDTextBox.setText(UUID.nameUUIDFromBytes(deviceInfos.getUuid()).toString());
					firmwareTextBox.setText(deviceInfos.getFirmwareMajor() + "." + deviceInfos.getFirmwareMinor());
					totalSizeTextBox.setText(FileUtils.byteCountToDisplaySize(deviceInfos.getSdCardSizeInBytes()));
					usedSizeTextBox.setText(FileUtils.byteCountToDisplaySize(deviceInfos.getUsedSpaceInBytes()));
					
					btnDownloadPackages.setEnabled(false);
					btnValidate.setEnabled(false);
					btnRefresh.setEnabled(false);
					
					fsDriverUsed = true;
					rawDriverUsed = false;
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
				
				
				loadPacksFromDevice().thenAcceptAsync( (packs) -> {
					devicePacksModel.clear();
					devicePacksModel.addAll(packs);
					
					btnDownloadPackages.setEnabled(true);
					btnValidate.setEnabled(true);
					btnRefresh.setEnabled(true);
				});
				
			}
		});
		
		rawDriver.registerDeviceListener(new DeviceHotplugEventListener() {
			
			@Override
			public void onDeviceUnplugged(Device device) {
				devicePacksModel.clear();
				serialNumberTextBox.setText("");
				uUIDTextBox.setText("");
				firmwareTextBox.setText("");
				totalSizeTextBox.setText("");
				usedSizeTextBox.setText("");
				
				btnDownloadPackages.setEnabled(false);
				btnValidate.setEnabled(false);
				btnRefresh.setEnabled(false);
				
			}
			
			@Override
			public void onDevicePlugged(Device device) {
				RawDeviceInfos deviceInfos;
				try {
					deviceInfos = rawDriver.getDeviceInfos().get();
					serialNumberTextBox.setText(deviceInfos.getSerialNumber());
					uUIDTextBox.setText(deviceInfos.getUuid().toString());
					//uUIDTextBox.setText(deviceInfos.getUuid().toString());
					firmwareTextBox.setText(deviceInfos.getFirmwareMajor() + "." + deviceInfos.getFirmwareMinor());
					totalSizeTextBox.setText(FileUtils.byteCountToDisplaySize(deviceInfos.getSdCardSizeInSectors()));
					usedSizeTextBox.setText(FileUtils.byteCountToDisplaySize(deviceInfos.getUsedSpaceInSectors()));
					
					btnDownloadPackages.setEnabled(false);
					btnValidate.setEnabled(false);
					btnRefresh.setEnabled(false);
					
					fsDriverUsed = false;
					rawDriverUsed = true;
					
					
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				loadPacksFromDevice().thenAcceptAsync( (packs) -> {
					devicePacksModel.clear();
					devicePacksModel.addAll(packs);
					
					btnDownloadPackages.setEnabled(true);
					btnValidate.setEnabled(true);
					btnRefresh.setEnabled(true);
					
				});
				
				
			}
		});
	
//		CompletableFuture.runAsync(() -> {
//			try {
//				fsDriver.simulateDevicePlugged();
//				fsDriverUsed = true;
//				rawDriverUsed = false;
//			} catch (Exception e) {
//				fsDriverUsed = false;
//				rawDriverUsed = false;
//			}
//		});
	}
	


	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmLuniiTransfer = new JFrame();
		frmLuniiTransfer.setIconImage(Toolkit.getDefaultToolkit().getImage(GUI.class.getResource("/lunii.png")));//.getDefaultToolkit().getImage("/Users/horfee/Developpement/studio.GoodOne/studio-desktop/src/main/resources/logolunii.jpeg"));
		frmLuniiTransfer.setTitle(localization.getString("Frame.Title"));
		frmLuniiTransfer.setBounds(100, 100, 1024, 720);
		frmLuniiTransfer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmLuniiTransfer.getContentPane().setLayout(new BoxLayout(frmLuniiTransfer.getContentPane(), BoxLayout.X_AXIS));
		
		JPanel panel = new JPanel();
		frmLuniiTransfer.getContentPane().add(panel);
		panel.setLayout(new BorderLayout(0, 0));
		
		JPanel panel_1 = new JPanel();
		panel.add(panel_1, BorderLayout.NORTH);
		GridBagLayout gbl_panel_1 = new GridBagLayout();
		gbl_panel_1.columnWidths = new int[] {0, 0, 0, 0};
		gbl_panel_1.rowHeights = new int[] {0, 0, 0, 0, 0, 0, 0};
		gbl_panel_1.columnWeights = new double[]{0.0, 1.0, 0.0, 0.0};
		gbl_panel_1.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		panel_1.setLayout(gbl_panel_1);
		
		JLabel deviceInformationLabel = new JLabel(localization.getString("Device.Information"));
		GridBagConstraints gbc_deviceInformationLabel = new GridBagConstraints();
		gbc_deviceInformationLabel.gridwidth = 4;
		gbc_deviceInformationLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_deviceInformationLabel.gridx = 0;
		gbc_deviceInformationLabel.gridy = 0;
		gbc_deviceInformationLabel.insets = new Insets(5, 5, 5, 5);
		panel_1.add(deviceInformationLabel, gbc_deviceInformationLabel);
		
		JLabel serialNumberLabel = new JLabel(localization.getString("Device.serialnum"));//"Serial number :");
		GridBagConstraints gbc_serialNumberLabel = new GridBagConstraints();
		gbc_serialNumberLabel.anchor = GridBagConstraints.EAST;
		gbc_serialNumberLabel.insets = new Insets(5, 5, 5, 5);
		gbc_serialNumberLabel.gridx = 0;
		gbc_serialNumberLabel.gridy = 1;
		panel_1.add(serialNumberLabel, gbc_serialNumberLabel);
		
		serialNumberTextBox = new JTextField();
		serialNumberTextBox.setEditable(false);
		GridBagConstraints gbc_serialNumberTextBox = new GridBagConstraints();
		gbc_serialNumberTextBox.weightx = 1.0;
		gbc_serialNumberTextBox.gridwidth = 3;
		gbc_serialNumberTextBox.insets = new Insets(5, 0, 5, 5);
		gbc_serialNumberTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_serialNumberTextBox.gridx = 1;
		gbc_serialNumberTextBox.gridy = 1;
		panel_1.add(serialNumberTextBox, gbc_serialNumberTextBox);
		serialNumberTextBox.setColumns(10);
		
		JLabel uUIDLabel = new JLabel(localization.getString("Device.UUID"));//:");
		GridBagConstraints gbc_uUIDLabel = new GridBagConstraints();
		gbc_uUIDLabel.anchor = GridBagConstraints.EAST;
		gbc_uUIDLabel.insets = new Insets(5, 5, 5, 5);
		gbc_uUIDLabel.gridx = 0;
		gbc_uUIDLabel.gridy = 2;
		panel_1.add(uUIDLabel, gbc_uUIDLabel);
		
		uUIDTextBox = new JTextField();
		uUIDTextBox.setEditable(false);
		GridBagConstraints gbc_uUIDTextBox = new GridBagConstraints();
		gbc_uUIDTextBox.weightx = 1.0;
		gbc_uUIDTextBox.gridwidth = 3;
		gbc_uUIDTextBox.insets = new Insets(5, 0, 5, 5);
		gbc_uUIDTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_uUIDTextBox.gridx = 1;
		gbc_uUIDTextBox.gridy = 2;
		panel_1.add(uUIDTextBox, gbc_uUIDTextBox);
		uUIDTextBox.setColumns(10);
		
		JLabel FirmwareLabel = new JLabel(localization.getString("Device.firmware"));//"Firmware :");
		GridBagConstraints gbc_FirmwareLabel = new GridBagConstraints();
		gbc_FirmwareLabel.anchor = GridBagConstraints.EAST;
		gbc_FirmwareLabel.insets = new Insets(5, 0, 5, 5);
		gbc_FirmwareLabel.gridx = 0;
		gbc_FirmwareLabel.gridy = 3;
		panel_1.add(FirmwareLabel, gbc_FirmwareLabel);
		
		firmwareTextBox = new JTextField();
		firmwareTextBox.setEditable(false);
		GridBagConstraints gbc_firmwareTextBox = new GridBagConstraints();
		gbc_firmwareTextBox.weightx = 1.0;
		gbc_firmwareTextBox.gridwidth = 3;
		gbc_firmwareTextBox.insets = new Insets(5, 0, 5, 5);
		gbc_firmwareTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_firmwareTextBox.gridx = 1;
		gbc_firmwareTextBox.gridy = 3;
		panel_1.add(firmwareTextBox, gbc_firmwareTextBox);
		firmwareTextBox.setColumns(10);
		
		JLabel sizeLabel = new JLabel(localization.getString("Device.size"));
		GridBagConstraints gbc_sizeLabel = new GridBagConstraints();
		gbc_sizeLabel.anchor = GridBagConstraints.EAST;
		gbc_sizeLabel.insets = new Insets(5, 5, 5, 5);
		gbc_sizeLabel.gridx = 0;
		gbc_sizeLabel.gridy = 4;
		panel_1.add(sizeLabel, gbc_sizeLabel);
		
		totalSizeTextBox = new JTextField();
		totalSizeTextBox.setEditable(false);
		GridBagConstraints gbc_totalSizeTextBox = new GridBagConstraints();
		gbc_totalSizeTextBox.weightx = 1.0;
		gbc_totalSizeTextBox.insets = new Insets(5, 0, 5, 5);
		gbc_totalSizeTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_totalSizeTextBox.gridx = 1;
		gbc_totalSizeTextBox.gridy = 4;
		panel_1.add(totalSizeTextBox, gbc_totalSizeTextBox);
		totalSizeTextBox.setColumns(10);
		
		JLabel sizeUsedLabel = new JLabel(localization.getString("Device.used"));
		GridBagConstraints gbc_sizeUsedLabel = new GridBagConstraints();
		gbc_sizeUsedLabel.insets = new Insets(5, 5, 5, 5);
		gbc_sizeUsedLabel.anchor = GridBagConstraints.EAST;
		gbc_sizeUsedLabel.gridx = 2;
		gbc_sizeUsedLabel.gridy = 4;
		panel_1.add(sizeUsedLabel, gbc_sizeUsedLabel);
		
		usedSizeTextBox = new JTextField();
		usedSizeTextBox.setEditable(false);
		GridBagConstraints gbc_usedSizeTextBox = new GridBagConstraints();
		gbc_usedSizeTextBox.weightx = 1.0;
		gbc_usedSizeTextBox.insets = new Insets(5, 0, 5, 5);
		gbc_usedSizeTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_usedSizeTextBox.gridx = 3;
		gbc_usedSizeTextBox.gridy = 4;
		panel_1.add(usedSizeTextBox, gbc_usedSizeTextBox);
		usedSizeTextBox.setColumns(10);
		
		JPanel panel_3 = new JPanel();
		panel.add(panel_3, BorderLayout.SOUTH);
		GridBagLayout gbl_panel_3 = new GridBagLayout();
		gbl_panel_3.columnWidths = new int[] {0, 0, 0, 0};
		gbl_panel_3.rowHeights = new int[] {0};
		gbl_panel_3.columnWeights = new double[]{0.0, 1.0, 0.0, 1.0};
		gbl_panel_3.rowWeights = new double[]{0.0};
		panel_3.setLayout(gbl_panel_3);
		
		lblTransferingPack = new Label();//"Transfering pack {}");
		GridBagConstraints gbc_lblTransferingPack = new GridBagConstraints();
		gbc_lblTransferingPack.anchor = GridBagConstraints.WEST;
		gbc_lblTransferingPack.insets = new Insets(5, 5, 5, 5);
		gbc_lblTransferingPack.gridx = 0;
		gbc_lblTransferingPack.gridy = 0;
		panel_3.add(lblTransferingPack, gbc_lblTransferingPack);
		
		installUninstallProgressBar = new JProgressBar();
		GridBagConstraints gbc_installUninstallProgressBar = new GridBagConstraints();
		gbc_installUninstallProgressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_installUninstallProgressBar.insets = new Insets(5, 0, 5, 5);
		gbc_installUninstallProgressBar.gridx = 1;
		gbc_installUninstallProgressBar.gridy = 0;
		panel_3.add(installUninstallProgressBar, gbc_installUninstallProgressBar);
		
		Label lblProgress = new Label(localization.getString("Transfer.globalProgress"));//"Progress");
		GridBagConstraints gbc_lblProgress = new GridBagConstraints();
		gbc_lblProgress.anchor = GridBagConstraints.WEST;
		gbc_lblProgress.insets = new Insets(5, 0, 5, 5);
		gbc_lblProgress.gridx = 2;
		gbc_lblProgress.gridy = 0;
		panel_3.add(lblProgress, gbc_lblProgress);
		
		globalProgressBar = new JProgressBar();
		GridBagConstraints gbc_globalProgressBar = new GridBagConstraints();
		gbc_globalProgressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_globalProgressBar.insets = new Insets(5, 0, 5, 5);
		gbc_globalProgressBar.gridx = 3;
		gbc_globalProgressBar.gridy = 0;
		panel_3.add(globalProgressBar, gbc_globalProgressBar);
		
		devicePacksModel = new DefaultListModel<JsonPack>();

		
		DragSource ds = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {

			@Override
			public void dragGestureRecognized(DragGestureEvent dge) {
				String source = dge.getComponent().getName();
				  if ( source.equals("devices") || source.equals("library") ) {		  
					  String selectedIndices = ((JList<JsonPack>)dge.getComponent()).getSelectedValuesList().stream().map( JsonPack::getUuid ).collect(Collectors.joining(","));
					  StringSelection transferable = new StringSelection(source + ": " + selectedIndices);
					  ds.startDrag(dge, DragSource.DefaultCopyDrop, transferable, null);		  
				  }				
			}};
		
		libraryPacksModel = new DefaultListModel<JsonPack>();
		devicePacksModel.addListDataListener(new ListDataListener() {

			@Override
			public void intervalAdded(ListDataEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/" + devicePacksList.getModel().getSize());
				
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/" + devicePacksList.getModel().getSize());
				
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/" + devicePacksList.getModel().getSize());
				
			}
			
		});
				libraryPacksModel.addListDataListener(new ListDataListener() {

					@Override
					public void intervalAdded(ListDataEvent e) {
						libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/" + libraryPacksModel.getSize());
						
					}

					@Override
					public void intervalRemoved(ListDataEvent e) {
						libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/" + libraryPacksModel.getSize());
						
					}

					@Override
					public void contentsChanged(ListDataEvent e) {
						libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/" + libraryPacksModel.getSize());
						
					}
					
				});
				
				JPanel panel_7 = new JPanel();
				panel_7.setBorder(null);
				panel.add(panel_7, BorderLayout.CENTER);
				GridBagLayout gbl_panel_7 = new GridBagLayout();
				gbl_panel_7.columnWidths = new int[]{0, 0};
				gbl_panel_7.rowHeights = new int[]{0, 0};
				gbl_panel_7.columnWeights = new double[]{1.0, Double.MIN_VALUE};
				gbl_panel_7.rowWeights = new double[]{1.0, Double.MIN_VALUE};
				panel_7.setLayout(gbl_panel_7);
				
				JSplitPane splitPane = new JSplitPane();
				splitPane.setResizeWeight(0.5);
				GridBagConstraints gbc_splitPane = new GridBagConstraints();
				gbc_splitPane.fill = GridBagConstraints.BOTH;
				gbc_splitPane.gridx = 0;
				gbc_splitPane.gridy = 0;
				gbc_splitPane.insets = new Insets(0, 5, 0, 5);
				panel_7.add(splitPane, gbc_splitPane);
				
				JPanel panel_2 = new JPanel();
				splitPane.setLeftComponent(panel_2);
				panel_2.setLayout(new BorderLayout(0, 0));
				
				JScrollPane devicePacksScrollPane = new JScrollPane();
				panel_2.add(devicePacksScrollPane);
				devicePacksList = new JList<JsonPack>(devicePacksModel);
				devicePacksList.addKeyListener(new KeyAdapter() {
					@Override
					public void keyTyped(KeyEvent e) {
						
						if ( e.getExtendedKeyCode() == KeyEvent.VK_DELETE ) {
							int[] selection = devicePacksList.getSelectedIndices();
							for(int i = selection.length - 1; i >= 0; i--) {
								devicePacksModel.remove(selection[i]);
							}
						} else if ( e.getExtendedKeyCode() == KeyEvent.VK_ESCAPE ) {
							devicePacksList.getSelectionModel().clearSelection();
						}
					}
				});
				devicePacksList.setBorder(new TitledBorder(null, localization.getString("Device.list"), TitledBorder.LEADING, TitledBorder.TOP, null, null));
				devicePacksList.setCellRenderer( new JsonPackCell());
				devicePacksList.setName("devices");
				devicePacksScrollPane.setViewportView(devicePacksList);
				devicePacksList.setDragEnabled(true);
				devicePacksList.setDropMode(DropMode.INSERT);
				devicePacksList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

					@Override
					public void valueChanged(ListSelectionEvent e) {
						devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/" + devicePacksList.getModel().getSize());
						
					}
					
				});
				ds.createDefaultDragGestureRecognizer(devicePacksList, DnDConstants.ACTION_MOVE, dgl);
				devicePacksList.setTransferHandler(new TransferHandler() {
					
					private static final long serialVersionUID = -6050701764610620507L;

					public boolean canImport(TransferHandler.TransferSupport support) {
					    if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
					      return false;
					    }
					    if ( !support.getComponent().getName().equals("devices")) {
					    	return false;
					    }
					    
					    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
					    return dl.getIndex() != -1;
					  }

					  public boolean importData(TransferHandler.TransferSupport support) {
					    if (!canImport(support)) {
					      return false;
					    }

					    Transferable transferable = support.getTransferable();
					    String transferMe;
						try {
							transferMe = (String)transferable.getTransferData(DataFlavor.stringFlavor);
						} catch (UnsupportedFlavorException | IOException e) {
							e.printStackTrace();
							return false;
						}
					    
					    String source = transferMe.substring(0, transferMe.indexOf(": "));
					    List<String> uuids = List.of(transferMe.substring(transferMe.indexOf(": ") + 2).split(","));

					    JList<JsonPack> list = source.equals("library") ? libraryPacksList : source.equals("devices") ? devicePacksList : null;
					    if ( list == null ) return false;
					    
					    final DefaultListModel<JsonPack> model = ((DefaultListModel<JsonPack>)list.getModel());
					    
					    List<Integer> indices = new ArrayList<Integer>();
					    for(String uuid :uuids ) {
					    	indices.add(IntStream.range(0, model.getSize())
					    		.filter( i -> uuid.equals(model.get(i).getUuid()))
					    		.findFirst()
					    		.getAsInt());
					    }
					    
					    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
					    final Integer dropTargetIndex = dl.getIndex();
				    	
					    if ( source.equals("devices") ) {
					    	int nbBeforeDropIndex = (int)indices.stream().filter( (v) -> v < dropTargetIndex.intValue() ).count();
					    	List<JsonPack> objects = new ArrayList<JsonPack>();
						    for(int index = indices.size() - 1; index >= 0; index-- ) {
						    	objects.add(model.remove(indices.get(index)));
						    }
						    
						    int insertIndex = dropTargetIndex - nbBeforeDropIndex;
						    
						    for(int i = objects.size() - 1; i >= 0; i-- ) {
						    	model.add(insertIndex++, objects.get(i));
						    	
						    }
					    } else {
					    	List<Object> existingPacks = List.of( ((DefaultListModel<JsonPack>)((JList<JsonPack>)support.getComponent()).getModel()).toArray());
					    	List<String> existingUUIDs = existingPacks.stream()
					    										.map( p -> ((JsonPack)p).getUuid() )
					    										.toList();
					    	
					    	List<JsonPack> toAdd = indices.stream()
					    									.map( i -> model.get(i))
					    									.filter( pack -> existingUUIDs.stream().allMatch(s -> !s.equalsIgnoreCase(pack.getUuid())) )
					    									.toList();
					    	devicePacksModel.addAll(dropTargetIndex, toAdd);
					    }

	    
					    return true;
					  }
					  
				});
				
				JPanel panel_4 = new JPanel();
				panel_2.add(panel_4, BorderLayout.SOUTH);
				GridBagLayout gbl_panel_4 = new GridBagLayout();
				gbl_panel_4.columnWeights = new double[]{0.0, 0.0, 0.0, 1.0};
				gbl_panel_4.rowWeights = new double[]{0.0};
				panel_4.setLayout(gbl_panel_4);
				
				btnDownloadPackages = new JButton(localization.getString("Device.download"));//"Download");
				btnDownloadPackages.setEnabled(false);
				GridBagConstraints gbc_btnDownloadPackages = new GridBagConstraints();
				gbc_btnDownloadPackages.anchor = GridBagConstraints.NORTHWEST;
				gbc_btnDownloadPackages.insets = new Insets(0, 0, 0, 5);
				gbc_btnDownloadPackages.gridx = 0;
				gbc_btnDownloadPackages.gridy = 0;
				panel_4.add(btnDownloadPackages, gbc_btnDownloadPackages);
				btnDownloadPackages.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						int[] selected = devicePacksList.getSelectedIndices();
						if ( selected.length == 0 ) {
							selected = IntStream.range(0, devicePacksModel.getSize()).toArray();
						}
						Arrays.stream(selected).boxed().map( (i) -> devicePacksModel.get(i)).forEach( (pack) -> {
							if ( fsDriverUsed ) {
								fsDriver.downloadPack(pack.getUuid(), library.getLibraryPath(), new TransferProgressListener() {
									
									@Override
									public void onProgress(TransferStatus status) {
										installUninstallProgressBar.setMinimum(0);
										installUninstallProgressBar.setMaximum(status.getTotal());
										installUninstallProgressBar.setValue(status.getTransferred());
										
									}
									
									@Override
									public void onComplete(TransferStatus status) {
										installUninstallProgressBar.setValue(installUninstallProgressBar.getMaximum());
										
									}
								});
							} else if ( rawDriverUsed ) {
								try {
									rawDriver.downloadPack(pack.getUuid(), Files.newOutputStream(Paths.get(library.getLibraryPath(), pack.getUuid() + ".pack")), new TransferProgressListener() {
										
										@Override
										public void onProgress(TransferStatus status) {
											installUninstallProgressBar.setMinimum(0);
											installUninstallProgressBar.setMaximum(status.getTotal());
											installUninstallProgressBar.setValue(status.getTransferred());
											
										}
										
										@Override
										public void onComplete(TransferStatus status) {
											installUninstallProgressBar.setValue(installUninstallProgressBar.getMaximum());
											
										}
									});
								} catch (IOException e1) {
									e1.printStackTrace();
								}
							}
						});
//				for(int i : selected) {
//					if ( fsDriverUsed ) {
//						fsDriver.downloadPack(null, null, null)
//					}
//				}
					}
				});
				
				btnValidate = new JButton(localization.getString("Device.validate"));//"Validate");
				btnValidate.setEnabled(false);
				GridBagConstraints gbc_btnValidate = new GridBagConstraints();
				gbc_btnValidate.anchor = GridBagConstraints.NORTHWEST;
				gbc_btnValidate.insets = new Insets(0, 0, 0, 5);
				gbc_btnValidate.gridx = 1;
				gbc_btnValidate.gridy = 0;
				panel_4.add(btnValidate, gbc_btnValidate);
				btnValidate.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						loadPacksFromDevice().thenAcceptAsync( (packs) -> {
							try {
							
							List<String> uuids = packs.stream().map( pack -> pack.getUuid()).toList();
							List<String> uuidsInList = Arrays.asList(devicePacksModel.toArray()).stream().map( pack -> ((JsonPack) pack).getUuid()).toList();
							
							// Packs to uninstall
							List<String> toUninstall = new ArrayList<String>(uuids);
							toUninstall.removeAll(uuidsInList);
							
							// Packs to add
							List<String> toInstall = new ArrayList<String>(uuidsInList);
							toInstall.removeAll(uuids);
							
							int nbSteps = toUninstall.size() + toInstall.size() + 1;
							
							globalProgressBar.setMinimum(0);
							globalProgressBar.setMaximum(nbSteps);
							globalProgressBar.setValue(0);
							if ( fsDriverUsed ) {
								toUninstall.forEach( (pack) -> {
									try {
										String packTitle = library.getPackForUUID(pack).get().getTitle();
										lblTransferingPack.setText(localization.getString("Transfering.deletingpack").replace("{}", packTitle));
										fsDriver.deletePack(pack).get();
									} catch (InterruptedException | ExecutionException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									} finally {
										globalProgressBar.setValue(globalProgressBar.getValue() + 1);								
									}
								});
							} else if ( rawDriverUsed ) {
								toUninstall.forEach( (pack) -> {
									try {
										String packTitle = library.getPackForUUID(pack).get().getTitle();
										lblTransferingPack.setText(localization.getString("Transfering.deletingpack").replace("{}", packTitle));
										rawDriver.deletePack(pack).get();
									} catch (InterruptedException | ExecutionException e1) {
										e1.printStackTrace();
									} finally {
										globalProgressBar.setValue(globalProgressBar.getValue() + 1);								
									}
								});
							}
							
							
							TransferProgressListener progressListener = new TransferProgressListener() {
								
								@Override
								public void onProgress(TransferStatus status) {
									installUninstallProgressBar.setMinimum(0);
									installUninstallProgressBar.setMaximum(status.getTotal());
									installUninstallProgressBar.setValue(status.getTransferred());
									
								}
								
								@Override
								public void onComplete(TransferStatus status) {
									installUninstallProgressBar.setValue(installUninstallProgressBar.getMaximum());
								}
							};
							
							if ( fsDriverUsed ) {
								toInstall.forEach( (pack) -> {
									try {
										String packTitle = library.getPackForUUID(pack).get().getTitle();
										lblTransferingPack.setText(localization.getString("Transfering.transferringpack").replace("{}", packTitle));
										fsDriver.uploadPack(pack, library.getFolderForUUID(pack), progressListener).get();
									} catch (InterruptedException | ExecutionException e1) {
										e1.printStackTrace();
									} finally {
										globalProgressBar.setValue(globalProgressBar.getValue() + 1);
									}
								});
							} else if ( rawDriverUsed ) {
								toInstall.forEach( (pack) -> {
									try {
										// TODO : getAckInputStream
										String packTitle = library.getPackForUUID(pack).get().getTitle();
										lblTransferingPack.setText(localization.getString("Transfering.transferringpack").replace("{}", packTitle));
										rawDriver.uploadPack(null, 0, progressListener).get();
									} catch (InterruptedException | ExecutionException e1) {
										e1.printStackTrace();
									} finally {
										globalProgressBar.setValue(globalProgressBar.getValue() + 1);								
									}
								});
							}
							
							// Reorder packs
							if ( fsDriverUsed ) {
								try {
									lblTransferingPack.setText(localization.getString("Transfering.reordering"));
									fsDriver.reorderPacks(uuidsInList).get();
								} catch (InterruptedException | ExecutionException e1) {
									e1.printStackTrace();
								} finally {
									globalProgressBar.setValue(globalProgressBar.getValue() + 1);							
								}
							} else {
								try {
									rawDriver.reorderPacks(uuidsInList).get();
								} catch (InterruptedException | ExecutionException e1) {
									e1.printStackTrace();
								} finally {
									globalProgressBar.setValue(globalProgressBar.getValue() + 1);
									
								}
							}
							
							} catch(Exception e2) {
								e2.printStackTrace();
							}
							
						});
					}
				});
				
				btnRefresh = new JButton(localization.getString("Device.refresh"));//"Refresh from device");
				btnRefresh.setEnabled(false);
				GridBagConstraints gbc_btnRefersh = new GridBagConstraints();
				gbc_btnRefersh.anchor = GridBagConstraints.NORTHWEST;
				gbc_btnRefersh.insets = new Insets(0, 0, 0, 5);
				gbc_btnRefersh.gridx = 2;
				gbc_btnRefersh.gridy = 0;
				panel_4.add(btnRefresh, gbc_btnRefersh);
				btnRefresh.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						btnDownloadPackages.setEnabled(false);
						btnValidate.setEnabled(false);
						btnRefresh.setEnabled(false);
						
						devicePacksModel.clear();
						loadPacksFromDevice().thenAcceptAsync( (packs) -> {
							devicePacksModel.clear();
							devicePacksModel.addAll(packs);
							
							btnDownloadPackages.setEnabled(true);
							btnValidate.setEnabled(true);
							btnRefresh.setEnabled(true);
						});
						
					}
				});
				
				devicePacksSummaryLabel = new JLabel("-/-");
				GridBagConstraints gbc_devicePacksSummaryLabel = new GridBagConstraints();
				gbc_devicePacksSummaryLabel.anchor = GridBagConstraints.EAST;
				gbc_devicePacksSummaryLabel.gridx = 3;
				gbc_devicePacksSummaryLabel.gridy = 0;
				panel_4.add(devicePacksSummaryLabel, gbc_devicePacksSummaryLabel);
				
				JPanel panel_5 = new JPanel();
				splitPane.setRightComponent(panel_5);
				panel_5.setLayout(new BorderLayout(0, 0));
				
				JPanel panel_6 = new JPanel();
				panel_5.add(panel_6, BorderLayout.SOUTH);
				GridBagLayout gbl_panel_6 = new GridBagLayout();
				gbl_panel_6.columnWeights = new double[]{0.0, 1.0};
				gbl_panel_6.rowWeights = new double[]{0.0};
				panel_6.setLayout(gbl_panel_6);
				
				btnRefreshLibrary = new JButton(localization.getString("Library.refresh"));//"Refresh library");
				btnRefreshLibrary.setEnabled(false);
				GridBagConstraints gbc_btnRefreshLibrary = new GridBagConstraints();
				gbc_btnRefreshLibrary.anchor = GridBagConstraints.NORTHWEST;
				gbc_btnRefreshLibrary.insets = new Insets(0, 0, 0, 5);
				gbc_btnRefreshLibrary.gridx = 0;
				gbc_btnRefreshLibrary.gridy = 0;
				panel_6.add(btnRefreshLibrary, gbc_btnRefreshLibrary);
				btnRefreshLibrary.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						btnRefreshLibrary.setEnabled(false);
						libraryPacksModel.clear();
						//libraryPacksList.setListData(new JsonPack[0]);
						library.refreshDatabase().thenRunAsync( () -> {					
							library.getPacks().thenAcceptAsync( (List<JsonPack> packs) -> {
								libraryPacksModel.addAll(packs);
								btnRefreshLibrary.setEnabled(true);
								
							});
						});
						
					}
				});
				
				libraryPacksSummaryLabel = new JLabel("-/-");
				GridBagConstraints gbc_libraryPacksSummaryLabel = new GridBagConstraints();
				gbc_libraryPacksSummaryLabel.anchor = GridBagConstraints.EAST;
				gbc_libraryPacksSummaryLabel.gridx = 1;
				gbc_libraryPacksSummaryLabel.gridy = 0;
				panel_6.add(libraryPacksSummaryLabel, gbc_libraryPacksSummaryLabel);
				
				
						
						JScrollPane libraryPacksScrollPane = new JScrollPane();
						panel_5.add(libraryPacksScrollPane);
						libraryPacksScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
						libraryPacksList = new JList<JsonPack>(libraryPacksModel);
						libraryPacksList.setBorder(new TitledBorder(null, localization.getString("Library.list"), TitledBorder.LEADING, TitledBorder.TOP, null, null));
						libraryPacksScrollPane.setViewportView(libraryPacksList);
						libraryPacksList.setName("library");
						libraryPacksList.setCellRenderer( new JsonPackCell());		
						libraryPacksList.setDragEnabled(true);
						libraryPacksList.addMouseListener( new MouseAdapter() {
							public void mouseClicked(MouseEvent evt) {								
								JList<JsonPack> list = (JList<JsonPack>)evt.getSource();
								if (evt.getClickCount() == 2) {
									int index = list.locationToIndex(evt.getPoint());
									if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
									    	Locale currentLocale = Locale.getDefault();
									    	JsonPack story = libraryPacksModel.get(index);
									    	String slug = story.getSlug();
//									    	if ( story.getPreviews() != null && story.getPreviews().size() > 0 ) {									    		
									    		try {
//									    			String storyName = story.getPreviews().get(0).split("/")[3];// story.getTitle().replaceAll(" ", "-").toLowerCase();
									    			String url = "https://lunii.com/" + currentLocale.getCountry().toLowerCase() + "-" + currentLocale.getLanguage() + "/luniistore-catalogue/" + slug;
									    			Desktop.getDesktop().browse(new URI(url));
									    		} catch (IOException | URISyntaxException e1) {
									    		}
//									    	} else {
//									    		story.getLocalizedInfos().entrySet().stream().filter( (infos) -> infos.getValue().getPreviews().size() > 0).findFirst().ifPresent( ( entrySet ) -> {
//									    			String storyName = entrySet.getValue().getPreviews().get(0).split("/")[3];// story.getTitle().replaceAll(" ", "-").toLowerCase();
//										    		String url = "https://lunii.com/" + currentLocale.getCountry().toLowerCase() + "-" + currentLocale.getLanguage() + "/luniistore-catalogue/" + storyName;
//										    		try {
//														Desktop.getDesktop().browse(new URI(url));
//													} catch (IOException | URISyntaxException e) {
//														// TODO Auto-generated catch block
//														e.printStackTrace();
//													}
//									    		});
//									    	}
										
									}
								}
							}
						});
						libraryPacksList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

							@Override
							public void valueChanged(ListSelectionEvent e) {
								libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/" + libraryPacksModel.getSize());
								
							}
							
						});
						libraryPacksList.addKeyListener(new KeyAdapter() {
							@Override
							public void keyTyped(KeyEvent e) {
								
								if ( e.getExtendedKeyCode() == KeyEvent.VK_ESCAPE ) {
									libraryPacksList.getSelectionModel().clearSelection();
								}
							}
						});
						

						ds.createDefaultDragGestureRecognizer(libraryPacksList, DnDConstants.ACTION_MOVE, dgl);
						
						libraryPacksList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		//TransferHandler transferHandler = libraryPacksList.getTransferHandler();
		
		JMenuBar menuBar = new JMenuBar();
		frmLuniiTransfer.setJMenuBar(menuBar);
		
		JMenu mnNewMenu = new JMenu(localization.getString("File.title"));//"File");
		mnNewMenu.setMnemonic('F');
		menuBar.add(mnNewMenu);
		
		JMenuItem mntmNewMenuItem = new JMenuItem(localization.getString("File.quit"));//"Quit");
		mntmNewMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frmLuniiTransfer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frmLuniiTransfer.dispatchEvent(new WindowEvent(frmLuniiTransfer, WindowEvent.WINDOW_CLOSING));

			}
		});
		
		JMenuItem changeLibraryPathMenuItem = new JMenuItem(localization.getString("Library.changePath"));//"Change library path...");
		changeLibraryPathMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				chooser.setCurrentDirectory(new java.io.File(library.getLibraryPath()));
			    chooser.setDialogTitle(localization.getString("Library.ChooseLibraryFolder"));//"Choose library folder");
			    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			    chooser.setAcceptAllFileFilterUsed(false);
			    if ( chooser.showOpenDialog(frmLuniiTransfer) == JFileChooser.APPROVE_OPTION ) {
			    	library = Library.getInstance(chooser.getSelectedFile().getAbsolutePath());
			    	btnRefreshLibrary.doClick();
			    }
			}
		});
		mnNewMenu.add(changeLibraryPathMenuItem);
		mntmNewMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.META_DOWN_MASK));
		mnNewMenu.add(mntmNewMenuItem);
	
		
	}

	
	
//

class MyListDropHandler<T> extends TransferHandler {
  private static final long serialVersionUID = -6453689410470088604L;
  
  JList<T> list;

  public MyListDropHandler(JList<T> list) {
    this.list = list;
  }

  public boolean canImport(TransferHandler.TransferSupport support) {
    if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      return false;
    }
    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
    return dl.getIndex() != -1;
  }

  public boolean importData(TransferHandler.TransferSupport support) {
    if (!canImport(support)) {
      return false;
    }

    Transferable transferable = support.getTransferable();
    Object indexString;
    try {
      indexString = (Object)transferable.getTransferData(DataFlavor.stringFlavor);
    } catch (Exception e) {
      return false;
    }

    List<Integer> indices = Arrays.stream(indexString.toString().split(",")).mapToInt(Integer::parseInt).boxed().toList();//.sorted(Collections.reverseOrder()).toList();
    JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
    final Integer dropTargetIndex = dl.getIndex();

    int nbBeforeDropIndex = (int)indices.stream().filter( (v) -> v < dropTargetIndex.intValue() ).count();
    
    List<T> objects = new ArrayList<T>();
    for(int index = indices.size() - 1; index >= 0; index-- ) {
    	objects.add(((DefaultListModel<T>)list.getModel()).remove(indices.get(index)));
    }
    
    int insertIndex = dropTargetIndex - nbBeforeDropIndex;
    
    for(int i = objects.size() - 1; i >= 0; i-- ) {
    	((DefaultListModel<T>)list.getModel()).add(insertIndex++, objects.get(i));
    	
    }
    
    return true;
  }
}
}
