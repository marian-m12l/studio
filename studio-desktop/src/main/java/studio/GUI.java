package studio;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.io.FileUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.usb4java.Device;

import net.harawata.appdirs.AppDirsFactory;
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
import studio.driver.raw.LibUsbMassStorageHelper;
import studio.driver.raw.RawStoryTellerAsyncDriver;

public class GUI {

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
	private boolean mustCancel = false;
	private GridBagLayout statusLayout;
	private JPanel statusPanel;
	private JButton btnCancel;
	private JLabel libraryPathLabel;
	private PlaceholderTextField librarySearchTextField;
	private PlaceholderTextField deviceSearchTextField;
	private JCheckBox displayOnlyNotOnDeviceCheckBox;
	private JPanel panel_1;
	private JComboBox languageFilterComboBox;
	private JLabel lblNewLabel;
	private JSpinner spinner;
	private JLabel lblNewLabel_1;
	private JSpinner spinner_1;
	private DefaultComboBoxModel<String> availableLanguagesModel;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {

		int javaVersion = Runtime.version().feature();
		if ( javaVersion < 14 ) {
			JOptionPane.showMessageDialog(null,
				    "You must install and use Java version 14+ to run this program",
				    "Unsupported version of java",
				    JOptionPane.ERROR_MESSAGE);

			System.exit(1);
		}
		
		
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

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
		if (fsDriverUsed) {
			return fsDriver.getPacksList().thenComposeAsync((List<FsStoryPackInfos> packs) -> {
				return library.loadDatabase().thenApplyAsync((Void v) -> {
					List<JsonPack> res = packs.stream().map(pack -> library.getPackForUUID(pack.getUuid().toString()))
							.filter(Optional::isPresent).map(Optional::get).toList();
					return res;
				});
			});
		} else if (rawDriverUsed) {
			return rawDriver.getPacksList().thenComposeAsync((List<RawStoryPackInfos> packs) -> {
				return library.loadDatabase().thenApplyAsync((Void v) -> {
					List<JsonPack> res = packs.stream().map(pack -> library.getPackForUUID(pack.getUuid().toString()))
							.filter(Optional::isPresent).map(Optional::get).toList();
					return res;
				});
			});
		} else {
			return CompletableFuture.supplyAsync(() -> new ArrayList<JsonPack>());
		}
	}
	
	private void initializeLunii() {		
		String libraryFolder = System.getProperty("user.home");
		File prefsFile = new File(AppDirsFactory.getInstance().getUserConfigDir("Lunii-Transfert", "0.4.1", "horfee") + "/prefs.ini");
		if ( prefsFile.exists()) {
			Properties prop = new java.util.Properties();
			try {
				prop.load(new FileInputStream(prefsFile));
			} catch (IOException e) {
			}
			libraryFolder = prop.getProperty("libraryPath");
			if ( libraryFolder == null ) {
				libraryFolder = System.getProperty("user.home");
			}
			
		}
		library = Library.getInstance(libraryFolder);

		libraryPathLabel.setText("Path: " + library.getLibraryPath());
		library.getPacks().thenAcceptAsync((List<JsonPack> packs) -> {
			libraryPacksModel.addAll(packs);
			btnRefreshLibrary.setEnabled(true);
			
			Set<String> languages = packs.stream().map( (JsonPack p) -> {
				return p.getLocalizedInfos() == null || p.getLocalizedInfos().isEmpty() ? null : p.getLocalizedInfos().keySet().iterator().next();
			}).collect(Collectors.toSet());
			if ( !languages.contains(null) ) {
				languages.add(null);
			}
			
			availableLanguagesModel.removeAllElements();
			availableLanguagesModel.addAll(languages);
		});

		fsDriver = new FsStoryTellerAsyncDriver(false);
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

				loadPacksFromDevice().thenAcceptAsync((packs) -> {
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
					// uUIDTextBox.setText(deviceInfos.getUuid().toString());
					firmwareTextBox.setText(deviceInfos.getFirmwareMajor() + "." + deviceInfos.getFirmwareMinor());
					totalSizeTextBox.setText(FileUtils.byteCountToDisplaySize(
							deviceInfos.getSdCardSizeInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE));
					usedSizeTextBox.setText(FileUtils.byteCountToDisplaySize(
							deviceInfos.getUsedSpaceInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE));

					btnDownloadPackages.setEnabled(false);
					btnValidate.setEnabled(false);
					btnRefresh.setEnabled(false);

					fsDriverUsed = false;
					rawDriverUsed = true;

				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				loadPacksFromDevice().thenAcceptAsync((packs) -> {
					devicePacksModel.clear();
					devicePacksModel.addAll(packs);

					btnDownloadPackages.setEnabled(true);
					btnValidate.setEnabled(true);
					btnRefresh.setEnabled(true);

				});

			}
		});

	}

	private void refreshFreeSpaceOnDevice() {
		try {
			if (fsDriverUsed) {
				FsDeviceInfos deviceInfos;
				deviceInfos = fsDriver.getDeviceInfos().get();

				usedSizeTextBox.setText(FileUtils.byteCountToDisplaySize(deviceInfos.getUsedSpaceInBytes()));

			} else if (rawDriverUsed) {
				RawDeviceInfos deviceInfos = rawDriver.getDeviceInfos().get();
				usedSizeTextBox.setText(FileUtils.byteCountToDisplaySize(
						deviceInfos.getUsedSpaceInSectors() * LibUsbMassStorageHelper.SECTOR_SIZE));

			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	
	private void filterLibrary() {
		String language = (String)languageFilterComboBox.getSelectedItem();
		int from = (int)spinner.getValue();
		int to = (int)spinner_1.getValue();
		
		library.getPacks().thenAcceptAsync( (List<JsonPack> packs) -> {
			Stream<JsonPack> stream = packs.stream();
			if ( language != null ) {
				stream = stream.filter( (JsonPack pack) -> {
					return pack.getLocalizedInfos().keySet().contains(language);
				});
			}
			if ( from > 0 ) {
				stream = stream.filter( (JsonPack pack) -> pack.getAgeMin() >= from);
			}
			if ( to > 0 ) {
				stream = stream.filter( (JsonPack pack) -> pack.getAgeMax() == -1 ? pack.getAgeMin() <= to : pack.getAgeMax() <= to);
			}
			libraryPacksModel.clear();
			libraryPacksModel.addAll(stream.collect(Collectors.toList()));
			
			
		});
		
		
	}
	
	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmLuniiTransfer = new JFrame();
		frmLuniiTransfer.setIconImage(Toolkit.getDefaultToolkit().getImage(GUI.class.getResource("/lunii.png")));// .getDefaultToolkit().getImage("/Users/horfee/Developpement/studio.GoodOne/studio-desktop/src/main/resources/logolunii.jpeg"));
		frmLuniiTransfer.setTitle(localization.getString("Frame.Title"));
		frmLuniiTransfer.setBounds(100, 100, 1024, 720);
		frmLuniiTransfer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmLuniiTransfer.getContentPane().setLayout(new BoxLayout(frmLuniiTransfer.getContentPane(), BoxLayout.X_AXIS));

		JPanel panel = new JPanel();
		frmLuniiTransfer.getContentPane().add(panel);
		panel.setLayout(new BorderLayout(0, 0));

		JPanel informationPanel = new JPanel();
		panel.add(informationPanel, BorderLayout.NORTH);
		GridBagLayout gbl_informationPanel = new GridBagLayout();
		gbl_informationPanel.columnWidths = new int[] { 0, 0, 0, 0 };
		gbl_informationPanel.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0 };
		gbl_informationPanel.columnWeights = new double[] { 0.0, 1.0, 0.0, 1.0 };
		gbl_informationPanel.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		informationPanel.setLayout(gbl_informationPanel);

		JLabel deviceInformationLabel = new JLabel(localization.getString("Device.Information"));
		GridBagConstraints gbc_deviceInformationLabel = new GridBagConstraints();
		gbc_deviceInformationLabel.gridwidth = 4;
		gbc_deviceInformationLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_deviceInformationLabel.gridx = 0;
		gbc_deviceInformationLabel.gridy = 0;
		gbc_deviceInformationLabel.insets = new Insets(5, 5, 5, 0);
		informationPanel.add(deviceInformationLabel, gbc_deviceInformationLabel);

		JLabel serialNumberLabel = new JLabel(localization.getString("Device.serialnum"));// "Serial number :");
		GridBagConstraints gbc_serialNumberLabel = new GridBagConstraints();
		gbc_serialNumberLabel.anchor = GridBagConstraints.EAST;
		gbc_serialNumberLabel.insets = new Insets(5, 5, 5, 5);
		gbc_serialNumberLabel.gridx = 0;
		gbc_serialNumberLabel.gridy = 1;
		informationPanel.add(serialNumberLabel, gbc_serialNumberLabel);

		serialNumberTextBox = new JTextField();
		serialNumberTextBox.setEditable(false);
		GridBagConstraints gbc_serialNumberTextBox = new GridBagConstraints();
		gbc_serialNumberTextBox.weightx = 1.0;
		gbc_serialNumberTextBox.gridwidth = 3;
		gbc_serialNumberTextBox.insets = new Insets(5, 0, 5, 0);
		gbc_serialNumberTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_serialNumberTextBox.gridx = 1;
		gbc_serialNumberTextBox.gridy = 1;
		informationPanel.add(serialNumberTextBox, gbc_serialNumberTextBox);
		serialNumberTextBox.setColumns(10);

		JLabel uUIDLabel = new JLabel(localization.getString("Device.UUID"));// :");
		GridBagConstraints gbc_uUIDLabel = new GridBagConstraints();
		gbc_uUIDLabel.anchor = GridBagConstraints.EAST;
		gbc_uUIDLabel.insets = new Insets(5, 5, 5, 5);
		gbc_uUIDLabel.gridx = 0;
		gbc_uUIDLabel.gridy = 2;
		informationPanel.add(uUIDLabel, gbc_uUIDLabel);

		uUIDTextBox = new JTextField();
		uUIDTextBox.setEditable(false);
		GridBagConstraints gbc_uUIDTextBox = new GridBagConstraints();
		gbc_uUIDTextBox.weightx = 1.0;
		gbc_uUIDTextBox.gridwidth = 3;
		gbc_uUIDTextBox.insets = new Insets(5, 0, 5, 0);
		gbc_uUIDTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_uUIDTextBox.gridx = 1;
		gbc_uUIDTextBox.gridy = 2;
		informationPanel.add(uUIDTextBox, gbc_uUIDTextBox);
		uUIDTextBox.setColumns(10);

		JLabel FirmwareLabel = new JLabel(localization.getString("Device.firmware"));// "Firmware :");
		GridBagConstraints gbc_FirmwareLabel = new GridBagConstraints();
		gbc_FirmwareLabel.anchor = GridBagConstraints.EAST;
		gbc_FirmwareLabel.insets = new Insets(5, 0, 5, 5);
		gbc_FirmwareLabel.gridx = 0;
		gbc_FirmwareLabel.gridy = 3;
		informationPanel.add(FirmwareLabel, gbc_FirmwareLabel);

		firmwareTextBox = new JTextField();
		firmwareTextBox.setEditable(false);
		GridBagConstraints gbc_firmwareTextBox = new GridBagConstraints();
		gbc_firmwareTextBox.weightx = 1.0;
		gbc_firmwareTextBox.gridwidth = 3;
		gbc_firmwareTextBox.insets = new Insets(5, 0, 5, 0);
		gbc_firmwareTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_firmwareTextBox.gridx = 1;
		gbc_firmwareTextBox.gridy = 3;
		informationPanel.add(firmwareTextBox, gbc_firmwareTextBox);
		firmwareTextBox.setColumns(10);

		JLabel sizeLabel = new JLabel(localization.getString("Device.size"));
		GridBagConstraints gbc_sizeLabel = new GridBagConstraints();
		gbc_sizeLabel.anchor = GridBagConstraints.EAST;
		gbc_sizeLabel.insets = new Insets(5, 5, 5, 5);
		gbc_sizeLabel.gridx = 0;
		gbc_sizeLabel.gridy = 4;
		informationPanel.add(sizeLabel, gbc_sizeLabel);

		totalSizeTextBox = new JTextField();
		totalSizeTextBox.setEditable(false);
		GridBagConstraints gbc_totalSizeTextBox = new GridBagConstraints();
		gbc_totalSizeTextBox.weightx = 1.0;
		gbc_totalSizeTextBox.insets = new Insets(5, 0, 5, 5);
		gbc_totalSizeTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_totalSizeTextBox.gridx = 1;
		gbc_totalSizeTextBox.gridy = 4;
		informationPanel.add(totalSizeTextBox, gbc_totalSizeTextBox);
		totalSizeTextBox.setColumns(10);

		JLabel sizeUsedLabel = new JLabel(localization.getString("Device.used"));
		GridBagConstraints gbc_sizeUsedLabel = new GridBagConstraints();
		gbc_sizeUsedLabel.insets = new Insets(5, 5, 5, 5);
		gbc_sizeUsedLabel.anchor = GridBagConstraints.EAST;
		gbc_sizeUsedLabel.gridx = 2;
		gbc_sizeUsedLabel.gridy = 4;
		informationPanel.add(sizeUsedLabel, gbc_sizeUsedLabel);

		usedSizeTextBox = new JTextField();
		usedSizeTextBox.setEditable(false);
		GridBagConstraints gbc_usedSizeTextBox = new GridBagConstraints();
		gbc_usedSizeTextBox.weightx = 1.0;
		gbc_usedSizeTextBox.insets = new Insets(5, 0, 5, 0);
		gbc_usedSizeTextBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_usedSizeTextBox.gridx = 3;
		gbc_usedSizeTextBox.gridy = 4;
		informationPanel.add(usedSizeTextBox, gbc_usedSizeTextBox);
		usedSizeTextBox.setColumns(10);

		libraryPathLabel = new JLabel("Library" + (library == null ? "" : library.getLibraryPath()));
		libraryPathLabel.setFont(new Font("Lucida Grande", Font.ITALIC, 10));
		GridBagConstraints gbc_libraryPathLabel = new GridBagConstraints();
		gbc_libraryPathLabel.gridwidth = 4;
		gbc_libraryPathLabel.anchor = GridBagConstraints.EAST;
		gbc_libraryPathLabel.gridx = 0;
		gbc_libraryPathLabel.gridy = 5;
		gbc_libraryPathLabel.insets = new Insets(5, 5, 5, 5);
		informationPanel.add(libraryPathLabel, gbc_libraryPathLabel);

		devicePacksModel = new DefaultListModel<JsonPack>();

		
		DragGestureListener dgl = new DragGestureListener() {

			@Override
			public void dragGestureRecognized(DragGestureEvent dge) {
				String source = dge.getComponent().getName();
				System.out.println("Recognized");
				if (source.equals("devices") || source.equals("library")) {
					String selectedIndices = ((JList<JsonPack>) dge.getComponent()).getSelectedValuesList().stream()
							.map(JsonPack::getUuid).collect(Collectors.joining(","));
					StringSelection transferable = new StringSelection(source + ": " + selectedIndices);
					System.out.println("start drag");
					dge.getDragSource().startDrag(dge, DragSource.DefaultMoveDrop, transferable, null);
				}
			}
		};

		DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(libraryPacksList, DnDConstants.ACTION_MOVE, dgl);
		libraryPacksModel = new DefaultListModel<JsonPack>();
		devicePacksModel.addListDataListener(new ListDataListener() {

			@Override
			public void intervalAdded(ListDataEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ devicePacksList.getModel().getSize());

			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ devicePacksList.getModel().getSize());

			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ devicePacksList.getModel().getSize());

			}

		});
		libraryPacksModel.addListDataListener(new ListDataListener() {

			@Override
			public void intervalAdded(ListDataEvent e) {
				libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ libraryPacksModel.getSize());

			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ libraryPacksModel.getSize());

			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ libraryPacksModel.getSize());

			}

		});

		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.5);
		panel.add(splitPane, BorderLayout.CENTER);


		JPanel deviceListPanel = new JPanel();
		splitPane.setLeftComponent(deviceListPanel);
		deviceListPanel.setLayout(new BorderLayout(0, 0));
		deviceListPanel.setBorder(new TitledBorder(null, localization.getString("Device.list"), TitledBorder.LEADING,
				TitledBorder.TOP, null, null));
		
		deviceSearchTextField = new PlaceholderTextField();
		deviceListPanel.add(deviceSearchTextField, BorderLayout.NORTH);
		deviceSearchTextField.setPlaceHolder(localization.getString("Device.search"));
		deviceSearchTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if ( e.getKeyChar() == KeyEvent.VK_ENTER ) {
					String searchText = deviceSearchTextField.getText().toLowerCase();
					boolean found = false;
					int i;
					int startIndex = devicePacksList.getSelectedIndex() + 1;
					for(i = startIndex; i < devicePacksModel.getSize() && !found; i++) {
						JsonPack element = devicePacksModel.get(i);
						found = element.getTitle() != null && element.getTitle().toLowerCase().contains(searchText) || element.getLocalizedInfos() != null && element.getLocalizedInfos().values().stream().anyMatch( (localizedInfos) -> (localizedInfos.getTitle() != null && localizedInfos.getTitle().toLowerCase().contains(searchText)) || (localizedInfos.getDescription() != null && localizedInfos.getDescription().toLowerCase().contains(searchText)));
					}
					if ( found ) {
						devicePacksList.setSelectedIndex(i - 1);
						devicePacksList.ensureIndexIsVisible(i - 1);
					} else if ( startIndex > 0 ) {
						devicePacksList.clearSelection();
						this.keyTyped((e));
					}
				} else if ( e.getKeyChar() == KeyEvent.VK_ESCAPE ) {
					deviceSearchTextField.setText("");
				}
			}
		});

		JScrollPane devicePacksScrollPane = new JScrollPane();
		deviceListPanel.add(devicePacksScrollPane);
		devicePacksList = new JList<JsonPack>(devicePacksModel);
		devicePacksList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {

				if (e.getExtendedKeyCode() == KeyEvent.VK_DELETE) {
					int[] selection = devicePacksList.getSelectedIndices();
					for (int i = selection.length - 1; i >= 0; i--) {
						devicePacksModel.remove(selection[i]);
					}
				} else if (e.getExtendedKeyCode() == KeyEvent.VK_ESCAPE) {
					devicePacksList.getSelectionModel().clearSelection();
				}
			}
		});
		devicePacksList.setCellRenderer(new JsonPackCell(localization));
		devicePacksList.setName("devices");
		devicePacksScrollPane.setViewportView(devicePacksList);
		//devicePacksList.setDragEnabled(true);
		devicePacksList.setDropMode(DropMode.INSERT);
		devicePacksList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				devicePacksSummaryLabel.setText(devicePacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ devicePacksList.getModel().getSize());

			}

		});
		DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(devicePacksList, DnDConstants.ACTION_MOVE, dgl);
		
		JPanel panel_4 = new JPanel();
		deviceListPanel.add(panel_4, BorderLayout.SOUTH);
		GridBagLayout gbl_panel_4 = new GridBagLayout();
		gbl_panel_4.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 1.0 };
		gbl_panel_4.rowWeights = new double[] { 0.0 };
		panel_4.setLayout(gbl_panel_4);

		btnDownloadPackages = new JButton(localization.getString("Device.download"));// "Download");
		btnDownloadPackages.setEnabled(false);
		GridBagConstraints gbc_btnDownloadPackages = new GridBagConstraints();
		gbc_btnDownloadPackages.anchor = GridBagConstraints.NORTHWEST;
		gbc_btnDownloadPackages.insets = new Insets(0, 0, 5, 5);
		gbc_btnDownloadPackages.gridx = 0;
		gbc_btnDownloadPackages.gridy = 0;
		panel_4.add(btnDownloadPackages, gbc_btnDownloadPackages);
		btnDownloadPackages.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				btnRefresh.setEnabled(false);
				btnDownloadPackages.setEnabled(false);
				btnValidate.setEnabled(false);
				btnCancel.setEnabled(true);

				CompletableFuture.runAsync(() -> {
					int[] selected = devicePacksList.getSelectedIndices();
					if (selected.length == 0) {
						selected = IntStream.range(0, devicePacksModel.getSize()).toArray();
					}
					globalProgressBar.setString("0/" + selected.length);
					globalProgressBar.setStringPainted(true);
					globalProgressBar.setMaximum(selected.length);

					Arrays.stream(selected).boxed().map((i) -> devicePacksModel.get(i)).forEach((pack) -> {
						boolean mustCancelLocal = false;
						synchronized (this) {
							mustCancelLocal = mustCancel;
						}
						if (fsDriverUsed && !mustCancelLocal) {
							try {

								JsonPack jsonPack = library.getPackForUUID(pack.getUuid()).get();
								String packTitle = jsonPack.getTitle();
								if (packTitle == null)
									packTitle = jsonPack.getLocalizedInfos().values().iterator().next().getTitle();
								lblTransferingPack.setText(localization.getString("Transfering.transferringpack")
										.replace("{}", packTitle));
								fsDriver.downloadPack(pack.getUuid(), library.getLibraryPath(),
										new TransferProgressListener() {

											@Override
											public void onProgress(TransferStatus status) {
												installUninstallProgressBar.setMinimum(0);
												installUninstallProgressBar.setMaximum(status.getTotal());
												installUninstallProgressBar.setValue(status.getTransferred());

											}

											@Override
											public void onComplete(TransferStatus status) {
											}
										}).get();

							} catch (InterruptedException | ExecutionException e1) {
								e1.printStackTrace();
							} finally {
								installUninstallProgressBar.setValue(installUninstallProgressBar.getMaximum());
								globalProgressBar.setValue(globalProgressBar.getValue() + 1);
								globalProgressBar
										.setString(globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
							}
						} else if (rawDriverUsed && !mustCancelLocal) {
							try {
								JsonPack jsonPack = library.getPackForUUID(pack.getUuid()).get();
								String packTitle = jsonPack.getTitle();
								if (packTitle == null)
									packTitle = jsonPack.getLocalizedInfos().values().iterator().next().getTitle();
								lblTransferingPack.setText(localization.getString("Transfering.transferringpack")
										.replace("{}", packTitle));

								rawDriver.downloadPack(pack.getUuid(),
										Files.newOutputStream(
												Paths.get(library.getLibraryPath(), pack.getUuid() + ".pack")),
										new TransferProgressListener() {

											@Override
											public void onProgress(TransferStatus status) {
												installUninstallProgressBar.setMinimum(0);
												installUninstallProgressBar.setMaximum(status.getTotal());
												installUninstallProgressBar.setValue(status.getTransferred());
											}

											@Override
											public void onComplete(TransferStatus status) {
											}
										}).get();
							} catch (IOException | InterruptedException | ExecutionException e1) {
								e1.printStackTrace();
							} finally {
								installUninstallProgressBar.setValue(installUninstallProgressBar.getMaximum());
								globalProgressBar.setValue(globalProgressBar.getValue() + 1);
								globalProgressBar
										.setString(globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
							}
						}
					});

					synchronized (this) {
						mustCancel = false;
					}

					lblTransferingPack.setText("");
					installUninstallProgressBar.setValue(0);
					globalProgressBar.setValue(0);
					globalProgressBar.setStringPainted(false);
					btnRefresh.setEnabled(true);
					btnDownloadPackages.setEnabled(true);
					btnValidate.setEnabled(true);
					btnCancel.setEnabled(false);
				});

			}
		});

		btnValidate = new JButton(localization.getString("Device.validate"));// "Validate");
		btnValidate.setEnabled(false);
		GridBagConstraints gbc_btnValidate = new GridBagConstraints();
		gbc_btnValidate.anchor = GridBagConstraints.NORTHWEST;
		gbc_btnValidate.insets = new Insets(0, 0, 5, 5);
		gbc_btnValidate.gridx = 1;
		gbc_btnValidate.gridy = 0;
		panel_4.add(btnValidate, gbc_btnValidate);
		btnValidate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				btnRefresh.setEnabled(false);
				btnValidate.setEnabled(false);
				btnDownloadPackages.setEnabled(false);
				btnCancel.setEnabled(true);

				loadPacksFromDevice().thenAcceptAsync((packs) -> {
					try {

						List<String> uuids = packs.stream().map(pack -> pack.getUuid()).toList();
						List<String> uuidsInList = Arrays.asList(devicePacksModel.toArray()).stream()
								.map(pack -> ((JsonPack) pack).getUuid()).toList();

						// Packs to uninstall
						List<String> toUninstall = new ArrayList<String>(uuids);
						toUninstall.removeAll(uuidsInList);

						// Packs to add
						List<String> toInstall = new ArrayList<String>(uuidsInList);
						toInstall.removeAll(uuids);

						int nbSteps = toUninstall.size() + toInstall.size() + 1;
						boolean mustCancelLocal = false;

						globalProgressBar.setMinimum(0);
						globalProgressBar.setMaximum(nbSteps);
						globalProgressBar.setValue(0);
						globalProgressBar.setString("0/" + nbSteps);
						globalProgressBar.setStringPainted(true);

						synchronized (this) {
							mustCancelLocal = mustCancel;
						}
						if (fsDriverUsed && !mustCancelLocal) {
							toUninstall.forEach((pack) -> {
								try {
									synchronized (this) {
										if (mustCancel)
											return;
									}
									JsonPack jsonPack = library.getPackForUUID(pack).get();
									String packTitle = jsonPack.getTitle();
									if (packTitle == null)
										packTitle = jsonPack.getLocalizedInfos().values().iterator().next().getTitle();
									lblTransferingPack.setText(localization.getString("Transfering.deletingpack")
											.replace("{}", packTitle));
									statusPanel.updateUI();
									fsDriver.deletePack(pack).get();

									refreshFreeSpaceOnDevice();
								} catch (InterruptedException | ExecutionException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								} finally {
									globalProgressBar.setValue(globalProgressBar.getValue() + 1);
									globalProgressBar.setString(
											globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
								}
							});
						} else if (rawDriverUsed && !mustCancelLocal) {
							toUninstall.forEach((pack) -> {
								try {
									synchronized (this) {
										if (mustCancel)
											return;
									}
									JsonPack jsonPack = library.getPackForUUID(pack).get();
									String packTitle = jsonPack.getTitle();
									if (packTitle == null)
										packTitle = jsonPack.getLocalizedInfos().values().iterator().next().getTitle();
									lblTransferingPack.setText(localization.getString("Transfering.deletingpack")
											.replace("{}", packTitle));
									statusPanel.updateUI();
									rawDriver.deletePack(pack).get();

									refreshFreeSpaceOnDevice();
								} catch (InterruptedException | ExecutionException e1) {
									e1.printStackTrace();
								} finally {
									globalProgressBar.setValue(globalProgressBar.getValue() + 1);
									globalProgressBar.setString(
											globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
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

						synchronized (this) {
							mustCancelLocal = mustCancel;
						}
						if (fsDriverUsed && !mustCancelLocal) {
							toInstall.forEach((pack) -> {
								try {
									synchronized (this) {
										if (mustCancel)
											return;
									}
									JsonPack jsonPack = library.getPackForUUID(pack).get();
									String packTitle = jsonPack.getTitle();
									if (packTitle == null)
										packTitle = jsonPack.getLocalizedInfos().values().iterator().next().getTitle();
									lblTransferingPack.setText(localization.getString("Transfering.transferringpack")
											.replace("{}", packTitle));
									statusPanel.updateUI();
									fsDriver.uploadPack(pack, library.getFolderForUUID(pack), progressListener).get();

									refreshFreeSpaceOnDevice();
								} catch (InterruptedException | ExecutionException e1) {
									e1.printStackTrace();
								} finally {
									globalProgressBar.setValue(globalProgressBar.getValue() + 1);
									globalProgressBar.setString(
											globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
								}
							});
						} else if (rawDriverUsed && !mustCancelLocal) {
							toInstall.forEach((pack) -> {
								try {
									synchronized (this) {
										if (mustCancel)
											return;
									}
									JsonPack jsonPack = library.getPackForUUID(pack).get();
									String packTitle = jsonPack.getTitle();
									if (packTitle == null)
										packTitle = jsonPack.getLocalizedInfos().values().iterator().next().getTitle();
									lblTransferingPack.setText(localization.getString("Transfering.transferringpack")
											.replace("{}", packTitle));
									statusPanel.updateUI();
									rawDriver.uploadPack(null, 0, progressListener).get();

									refreshFreeSpaceOnDevice();
								} catch (InterruptedException | ExecutionException e1) {
									e1.printStackTrace();
								} finally {
									globalProgressBar.setValue(globalProgressBar.getValue() + 1);
									globalProgressBar.setString(
											globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
								}
							});
						}

						// Reorder packs
						synchronized (this) {
							mustCancelLocal = mustCancel;
						}
						if (fsDriverUsed && !mustCancelLocal) {
							try {
								lblTransferingPack.setText(localization.getString("Transfering.reordering"));
								statusPanel.updateUI();

								fsDriver.reorderPacks(uuidsInList).get();
							} catch (InterruptedException | ExecutionException e1) {
								e1.printStackTrace();
							} finally {
								globalProgressBar.setValue(globalProgressBar.getValue() + 1);
								globalProgressBar
										.setString(globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());
							}
						} else {
							try {
								rawDriver.reorderPacks(uuidsInList).get();
							} catch (InterruptedException | ExecutionException e1) {
								e1.printStackTrace();
							} finally {
								globalProgressBar.setValue(globalProgressBar.getValue() + 1);
								globalProgressBar
										.setString(globalProgressBar.getValue() + "/" + globalProgressBar.getMaximum());

							}
						}

					} catch (Exception e2) {
						e2.printStackTrace();
					}

					synchronized (this) {
						mustCancel = false;
					}
					btnRefresh.setEnabled(true);
					btnValidate.setEnabled(true);
					btnCancel.setEnabled(false);
					btnDownloadPackages.setEnabled(true);
					lblTransferingPack.setText(localization.getString("Transfering.done"));

					installUninstallProgressBar.setValue(0);
					globalProgressBar.setValue(0);
					globalProgressBar.setString(null);
					statusPanel.updateUI();
				});
			}
		});

		btnRefresh = new JButton(localization.getString("Device.refresh"));
		btnRefresh.setEnabled(false);
		GridBagConstraints gbc_btnRefersh = new GridBagConstraints();
		gbc_btnRefersh.anchor = GridBagConstraints.NORTHWEST;
		gbc_btnRefersh.insets = new Insets(0, 0, 5, 5);
		gbc_btnRefersh.gridx = 2;
		gbc_btnRefersh.gridy = 0;
		panel_4.add(btnRefresh, gbc_btnRefersh);
		btnRefresh.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				btnDownloadPackages.setEnabled(false);
				btnValidate.setEnabled(false);
				btnRefresh.setEnabled(false);

				devicePacksModel.clear();
				loadPacksFromDevice().thenAcceptAsync((packs) -> {
					devicePacksModel.clear();
					devicePacksModel.addAll(packs);

					btnDownloadPackages.setEnabled(true);
					btnValidate.setEnabled(true);
					btnRefresh.setEnabled(true);
				});

			}
		});

		btnCancel = new JButton(localization.getString("Device.cancel"));

		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				synchronized (this) {
					mustCancel = true;
				}
				btnCancel.setEnabled(false);
			}
		});
		btnCancel.setEnabled(false);
		GridBagConstraints gbc_btnCancel = new GridBagConstraints();
		gbc_btnCancel.anchor = GridBagConstraints.NORTHWEST;
		gbc_btnCancel.insets = new Insets(0, 0, 5, 5);
		gbc_btnCancel.gridx = 3;
		gbc_btnCancel.gridy = 0;
		panel_4.add(btnCancel, gbc_btnCancel);

		devicePacksSummaryLabel = new JLabel("-/-");
		GridBagConstraints gbc_devicePacksSummaryLabel = new GridBagConstraints();
		gbc_devicePacksSummaryLabel.insets = new Insets(0, 0, 5, 5);
		gbc_devicePacksSummaryLabel.anchor = GridBagConstraints.EAST;
		gbc_devicePacksSummaryLabel.gridx = 4;
		gbc_devicePacksSummaryLabel.gridy = 0;
		panel_4.add(devicePacksSummaryLabel, gbc_devicePacksSummaryLabel);

		JPanel libraryListPanel = new JPanel();
		splitPane.setRightComponent(libraryListPanel);
		libraryListPanel.setLayout(new BorderLayout(0, 0));
		libraryListPanel.setBorder(new TitledBorder(null, localization.getString("Library.list"), TitledBorder.LEADING,
				TitledBorder.TOP, null, null));

		JScrollPane libraryPacksScrollPane = new JScrollPane();
		libraryListPanel.add(libraryPacksScrollPane);
		libraryPacksList = new JList<JsonPack>(libraryPacksModel);
		devicePacksList.setTransferHandler(new DeviceListTransferHandler(libraryPacksList, devicePacksList));

		libraryPacksScrollPane.setViewportView(libraryPacksList);
		libraryPacksList.setName("library");
		libraryPacksList.setCellRenderer(new JsonPackCell(localization));
		//libraryPacksList.setDragEnabled(true);
		libraryPacksList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {
				JList<JsonPack> list = (JList<JsonPack>) evt.getSource();
				if (evt.getClickCount() == 2) {
					int index = list.locationToIndex(evt.getPoint());
					if (index == -1)
						return;
					if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
						Locale currentLocale = Locale.getDefault();
						JsonPack story = libraryPacksModel.get(index);
						String slug = story.getSlug();

						try {
							String url = "https://lunii.com/" + currentLocale.getCountry().toLowerCase() + "-"
									+ currentLocale.getLanguage() + "/luniistore-catalogue/" + slug;
							Desktop.getDesktop().browse(new URI(url));
						} catch (IOException | URISyntaxException e1) {
						}

					}
				}
			}
		});
		libraryPacksList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				libraryPacksSummaryLabel.setText(libraryPacksList.getSelectionModel().getSelectedItemsCount() + "/"
						+ libraryPacksModel.getSize());

			}

		});
		libraryPacksList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {

				if (e.getExtendedKeyCode() == KeyEvent.VK_ESCAPE) {
					libraryPacksList.getSelectionModel().clearSelection();
				}
			}
		});

		DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(libraryPacksList, DnDConstants.ACTION_MOVE, dgl);

		libraryPacksList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		JPanel panel_6 = new JPanel();
		libraryListPanel.add(panel_6, BorderLayout.SOUTH);
		GridBagLayout gbl_panel_6 = new GridBagLayout();
		gbl_panel_6.columnWeights = new double[] { 0.0, 0.0, 1.0 };
		panel_6.setLayout(gbl_panel_6);
		panel_6.setMinimumSize(new Dimension(100, 100));

		btnRefreshLibrary = new JButton(localization.getString("Library.refresh"));
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
				library.refreshDatabase().thenRunAsync(() -> {
					library.getPacks().thenAcceptAsync((List<JsonPack> packs) -> {
						Set<String> languages = packs.stream().map( (JsonPack p) -> {
							return p.getLocalizedInfos() == null || p.getLocalizedInfos().isEmpty() ? null : p.getLocalizedInfos().keySet().iterator().next();
						}).collect(Collectors.toSet());
						if ( !languages.contains(null) ) {
							languages.add(null);
						}
						availableLanguagesModel.removeAllElements();
						availableLanguagesModel.addAll(languages);
						libraryPacksModel.addAll(packs);
						btnRefreshLibrary.setEnabled(true);

					});
				});

			}
		});
		
		displayOnlyNotOnDeviceCheckBox = new JCheckBox(localization.getString("Library.filteronlynotpresentonbox"));
		displayOnlyNotOnDeviceCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean filter = displayOnlyNotOnDeviceCheckBox.isSelected();
				if ( filter ) {
					library.getPacks().thenAcceptAsync( (List<JsonPack> list) -> {
						libraryPacksModel.clear();
						List<JsonPack> filtered = list.stream().filter( (JsonPack p) -> {
							return !devicePacksModel.contains(p);
						}).toList();
						libraryPacksModel.addAll(filtered);
					});
				} else {
					library.getPacks().thenAcceptAsync( (List<JsonPack> list) -> {
						libraryPacksModel.clear();
						libraryPacksModel.addAll(list);
					});
				}
			}
		});
		GridBagConstraints gbc_displayOnlyNotOnDeviceCheckBox = new GridBagConstraints();
		gbc_displayOnlyNotOnDeviceCheckBox.anchor = GridBagConstraints.NORTHWEST;
		gbc_displayOnlyNotOnDeviceCheckBox.insets = new Insets(0, 0, 0, 5);
		gbc_displayOnlyNotOnDeviceCheckBox.gridx = 1;
		gbc_displayOnlyNotOnDeviceCheckBox.gridy = 0;
		panel_6.add(displayOnlyNotOnDeviceCheckBox, gbc_displayOnlyNotOnDeviceCheckBox);

		libraryPacksSummaryLabel = new JLabel("-/-");
		GridBagConstraints gbc_libraryPacksSummaryLabel = new GridBagConstraints();
		gbc_libraryPacksSummaryLabel.anchor = GridBagConstraints.EAST;
		gbc_libraryPacksSummaryLabel.gridx = 2;
		gbc_libraryPacksSummaryLabel.gridy = 0;
		panel_6.add(libraryPacksSummaryLabel, gbc_libraryPacksSummaryLabel);
		
		panel_1 = new JPanel();
		libraryListPanel.add(panel_1, BorderLayout.NORTH);
		GridBagLayout gbl_panel_1 = new GridBagLayout();
		//gbl_panel_1.columnWidths = new int[] {51, 52, 60, 60, 60, 60, 30};
		//gbl_panel_1.rowHeights = new int[]{27, 0};
		gbl_panel_1.columnWeights = new double[]{0.0, 1.0, 0.0, 0.0, 0.0, 0.0};
		gbl_panel_1.rowWeights = new double[]{0.0};
		panel_1.setLayout(gbl_panel_1);
		
		
		availableLanguagesModel = new DefaultComboBoxModel<String>(); 
		languageFilterComboBox = new JComboBox<String>(availableLanguagesModel);
		languageFilterComboBox.setRenderer(new LanguagesCellRenderer());
		languageFilterComboBox.setMinimumSize(new Dimension(70, 20));
		languageFilterComboBox.setPreferredSize(new Dimension(70, 20));
		GridBagConstraints gbc_comboBox = new GridBagConstraints();
		gbc_comboBox.anchor = GridBagConstraints.WEST;
		gbc_comboBox.insets = new Insets(0, 0, 0, 5);
		gbc_comboBox.gridx = 0;
		gbc_comboBox.gridy = 0;
		panel_1.add(languageFilterComboBox, gbc_comboBox);
		languageFilterComboBox.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				filterLibrary();

			}
		});

		librarySearchTextField = new PlaceholderTextField();
		GridBagConstraints gbc_librarySearchTextField = new GridBagConstraints();
		gbc_librarySearchTextField.weightx = 10.0;
		gbc_librarySearchTextField.fill = GridBagConstraints.HORIZONTAL;
		gbc_librarySearchTextField.anchor = GridBagConstraints.NORTH;
		gbc_librarySearchTextField.insets = new Insets(0, 0, 0, 5);
		gbc_librarySearchTextField.gridx = 1;
		gbc_librarySearchTextField.gridy = 0;
		panel_1.add(librarySearchTextField, gbc_librarySearchTextField);
		librarySearchTextField.setPlaceHolder(localization.getString("Library.search"));
		librarySearchTextField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if ( e.getKeyChar() == KeyEvent.VK_ENTER ) {
					String searchText = librarySearchTextField.getText().toLowerCase();
					boolean found = false;
					int i;
					int startIndex = libraryPacksList.getSelectedIndex() + 1;
					for(i = startIndex; i < libraryPacksModel.getSize() && !found; i++) {
						JsonPack element = libraryPacksModel.get(i);
						found = element.getTitle() != null && element.getTitle().toLowerCase().contains(searchText) || element.getLocalizedInfos() != null && element.getLocalizedInfos().values().stream().anyMatch( (localizedInfos) -> (localizedInfos.getTitle() != null && localizedInfos.getTitle().toLowerCase().contains(searchText)) || (localizedInfos.getDescription() != null && localizedInfos.getDescription().toLowerCase().contains(searchText)));
					}
					if ( found ) {
						libraryPacksList.setSelectedIndex(i - 1);
						libraryPacksList.ensureIndexIsVisible(i - 1);
					} else if ( startIndex > 0 ) {
						libraryPacksList.clearSelection();
						this.keyTyped((e));
					}
				} else if ( e.getKeyChar() == KeyEvent.VK_ESCAPE ) {
					librarySearchTextField.setText("");
				}
			}
		});
		
		lblNewLabel = new JLabel(localization.getString("Library.searchFrom"));
		lblNewLabel.setHorizontalAlignment(SwingConstants.TRAILING);
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.EAST;
		//gbc_lblNewLabel.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel.insets = new Insets(0, 0, 0, 5);
		gbc_lblNewLabel.gridx = 2;
		gbc_lblNewLabel.gridy = 0;
		panel_1.add(lblNewLabel, gbc_lblNewLabel);
		
		spinner = new JSpinner(new SpinnerNumberModel(0, 0, 13, 1));
		GridBagConstraints gbc_spinner = new GridBagConstraints();
		gbc_spinner.anchor = GridBagConstraints.NORTHEAST;
		gbc_spinner.insets = new Insets(0, 0, 0, 5);
		gbc_spinner.gridx = 3;
		gbc_spinner.gridy = 0;
		panel_1.add(spinner, gbc_spinner);
		spinner.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				filterLibrary();
				
			}
		});
		
		lblNewLabel_1 = new JLabel(localization.getString("Library.searchTo"));
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 0, 5);
		gbc_lblNewLabel_1.gridx = 4;
		gbc_lblNewLabel_1.gridy = 0;
		panel_1.add(lblNewLabel_1, gbc_lblNewLabel_1);
		
		spinner_1 = new JSpinner(new SpinnerNumberModel(0, 0, 13, 1));
		GridBagConstraints gbc_spinner_1 = new GridBagConstraints();
		gbc_spinner_1.insets = new Insets(0, 0, 0, 5);
		gbc_spinner_1.anchor = GridBagConstraints.NORTHEAST;
		gbc_spinner_1.gridx = 5;
		gbc_spinner_1.gridy = 0;
		panel_1.add(spinner_1, gbc_spinner_1);
		spinner_1.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				filterLibrary();
				
			}
		});

		statusPanel = new JPanel();
		panel.add(statusPanel, BorderLayout.SOUTH);
		statusLayout = new GridBagLayout();
		statusLayout.columnWeights = new double[] { 0.0, 1.0, 0.0, 1.0 };
		statusPanel.setLayout(statusLayout);

		lblTransferingPack = new Label(localization.getString("Transfer.notransfer"));
		GridBagConstraints gbc_lblTransferingPack = new GridBagConstraints();
		gbc_lblTransferingPack.fill = GridBagConstraints.HORIZONTAL;
		gbc_lblTransferingPack.insets = new Insets(0, 5, 0, 5);
		gbc_lblTransferingPack.gridy = 0;
		gbc_lblTransferingPack.gridx = 0;
		statusPanel.add(lblTransferingPack, gbc_lblTransferingPack);

		installUninstallProgressBar = new JProgressBar();
		GridBagConstraints gbc_installUninstallProgressBar = new GridBagConstraints();
		gbc_installUninstallProgressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_installUninstallProgressBar.insets = new Insets(0, 0, 0, 5);
		gbc_installUninstallProgressBar.gridx = 1;
		gbc_installUninstallProgressBar.gridy = 0;
		statusPanel.add(installUninstallProgressBar, gbc_installUninstallProgressBar);

		Label lblProgress = new Label(localization.getString("Transfer.globalProgress"));
		GridBagConstraints gbc_lblProgress = new GridBagConstraints();
		gbc_lblProgress.fill = GridBagConstraints.HORIZONTAL;
		gbc_lblProgress.insets = new Insets(0, 0, 0, 5);
		gbc_lblProgress.gridx = 2;
		gbc_lblProgress.gridy = 0;
		statusPanel.add(lblProgress, gbc_lblProgress);

		globalProgressBar = new JProgressBar();
		globalProgressBar.setStringPainted(false);
		GridBagConstraints gbc_globalProgressBar = new GridBagConstraints();
		gbc_globalProgressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_globalProgressBar.insets = new Insets(0, 0, 0, 5);
		gbc_globalProgressBar.gridx = 3;
		gbc_globalProgressBar.gridy = 0;
		statusPanel.add(globalProgressBar, gbc_globalProgressBar);
		GridBagConstraints gbc_splitPane = new GridBagConstraints();
		gbc_splitPane.fill = GridBagConstraints.BOTH;
		gbc_splitPane.gridx = 0;
		gbc_splitPane.gridy = 0;
		gbc_splitPane.insets = new Insets(0, 5, 0, 5);

		JMenuBar menuBar = new JMenuBar();
		frmLuniiTransfer.setJMenuBar(menuBar);

		JMenu mnNewMenu = new JMenu(localization.getString("File.title"));// "File");
		mnNewMenu.setMnemonic('F');
		menuBar.add(mnNewMenu);

		JMenuItem mntmNewMenuItem = new JMenuItem(localization.getString("File.quit"));// "Quit");
		mntmNewMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frmLuniiTransfer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frmLuniiTransfer.dispatchEvent(new WindowEvent(frmLuniiTransfer, WindowEvent.WINDOW_CLOSING));

			}
		});
		

		JMenuItem changeLibraryPathMenuItem = new JMenuItem(localization.getString("Library.changePath"));

		changeLibraryPathMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				chooser.setCurrentDirectory(new java.io.File(library.getLibraryPath()));
				chooser.setDialogTitle(localization.getString("Library.ChooseLibraryFolder"));

				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setAcceptAllFileFilterUsed(false);
				if (chooser.showOpenDialog(frmLuniiTransfer) == JFileChooser.APPROVE_OPTION) {
					String path = chooser.getSelectedFile().getAbsolutePath();
					Properties prop = new java.util.Properties();
					prop.setProperty("libraryPath", path);
					File file = new File(AppDirsFactory.getInstance().getUserConfigDir("Lunii-Transfert", "0.4.1", "horfee"));
					if ( !file.exists()) {
						file.mkdirs();
					}
					file = new File(file.getAbsolutePath() + "/prefs.ini");
					try(FileOutputStream fos = new FileOutputStream( file )) {
						prop.store(fos, "auto-generated");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					
					library = Library.getInstance(path);
					btnRefreshLibrary.doClick();
					libraryPathLabel.setText(localization.getString("Library.infoPath").replace("{}", library.getLibraryPath()));
				}
			}
		});
		mnNewMenu.add(changeLibraryPathMenuItem);

		JMenuItem importMenuItem = new JMenuItem(localization.getString("File.import"));
		importMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(localization.getString("Import.ChooseImportEntry"));
				chooser.setMultiSelectionEnabled(true);
				if (chooser.showOpenDialog(frmLuniiTransfer) == JFileChooser.APPROVE_OPTION) {
					for (File f : chooser.getSelectedFiles()) {
						if (f.isDirectory()) {
							for (File toImport : f.listFiles()) {
								library.tryToImport(toImport.toPath());
							}
						} else {
							library.tryToImport(f.toPath());
						}
					}
					btnRefreshLibrary.doClick();
				}
			}
		});
		mnNewMenu.add(importMenuItem);
		
		JMenuItem exportMenuItem = new JMenuItem(localization.getString("File.export"));
		exportMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(localization.getString("Export.SaveFile"));
				chooser.setMultiSelectionEnabled(true);
				chooser.setAcceptAllFileFilterUsed(false);
				
				if (chooser.showSaveDialog(frmLuniiTransfer) == JFileChooser.APPROVE_OPTION) {
					int response = JOptionPane.showConfirmDialog(frmLuniiTransfer, "Export entire library ?", "Export library", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
					if ( response == JOptionPane.CANCEL_OPTION ) {
						return;
					}
					
					List<JsonPack> data = new ArrayList<>();
					List<String> downloadedLibrary = Collections.list(libraryPacksModel.elements()).stream().map( (JsonPack pack) -> pack.getUuid()).toList();
					List<String> downloadedOnDevice = Collections.list(devicePacksModel.elements()).stream().map( (JsonPack pack) -> pack.getUuid()).toList();
					
					if ( response == JOptionPane.YES_OPTION ) {
						try {
							data = library.getAllLuniiPacks().get();
						} catch (InterruptedException | ExecutionException e1) {
							e1.printStackTrace();
							return;
						}
					} else if ( response == JOptionPane.NO_OPTION ) {
						data = Collections.list(libraryPacksModel.elements());
						
					}
					
					XSSFWorkbook workbook = new XSSFWorkbook();
					XSSFSheet sheet = workbook.createSheet("Library");
//					sheet.setColumnWidth(0, 6000);
//					sheet.setColumnWidth(1, 4000);

					XSSFRow header = sheet.createRow(0);

					CellStyle headerStyle = workbook.createCellStyle();
					headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
					headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

					XSSFFont font = ((XSSFWorkbook) workbook).createFont();
					font.setFontName("Arial");
					font.setFontHeightInPoints((short) 16);
					font.setBold(true);
					headerStyle.setFont(font);

					XSSFCell headerCell = header.createCell(0);
					headerCell.setCellValue("Ref");
					headerCell.setCellStyle(headerStyle);
					
					headerCell = header.createCell(1);
					headerCell.setCellValue("Title");
					headerCell.setCellStyle(headerStyle);

					headerCell = header.createCell(2);
					headerCell.setCellValue("Age - From");
					headerCell.setCellStyle(headerStyle);
										
					headerCell = header.createCell(3);
					headerCell.setCellValue("Age - To");
					headerCell.setCellStyle(headerStyle);
					
					headerCell = header.createCell(4);
					headerCell.setCellValue("Link");
					headerCell.setCellStyle(headerStyle);
					
					headerCell = header.createCell(5);
					headerCell.setCellValue("Downloaded");
					headerCell.setCellStyle(headerStyle);
					
					headerCell = header.createCell(6);
					headerCell.setCellValue("Present on the device");
					headerCell.setCellStyle(headerStyle);
					
					headerCell = header.createCell(7);
					headerCell.setCellValue("Locale");
					headerCell.setCellStyle(headerStyle);

					sheet.setAutoFilter(new CellRangeAddress(0,0,0,7));

					Locale locale = Locale.getDefault();
					data.forEach( (JsonPack pack) -> {
						XSSFRow dataRow = sheet.createRow(sheet.getLastRowNum() + 1);
						XSSFCell dataCell = dataRow.createCell(0);
						dataCell.setCellValue(pack.getUuid());
						String usedLocale = locale.getDisplayLanguage();
						
						dataCell = dataRow.createCell(1);
						String title = pack.getTitle();
						if ( title == null || title.length() == 0 ) {
							title = pack.getResolvedTitle(locale);
							if ( title == null || title.length() == 0 ) {
								usedLocale = pack.getLocalizedInfos().keySet().iterator().next().replace("_", "-");								
								title = pack.getResolvedTitle(Locale.forLanguageTag(usedLocale));
								usedLocale = Locale.forLanguageTag(usedLocale).getDisplayLanguage();
								
							}
						}
						dataCell.setCellValue(title);
						
						dataCell = dataRow.createCell(2);
						if ( pack.getAgeMin() != -1 ) {
							dataCell.setCellValue(pack.getAgeMin());
						}
						
						dataCell = dataRow.createCell(3);
						if ( pack.getAgeMax() != -1 ) {
							dataCell.setCellValue(pack.getAgeMax());
						}
						
						dataCell = dataRow.createCell(4);
						String url = "https://lunii.com/" + locale.getCountry().toLowerCase() + "-" + locale.getLanguage() + "/luniistore-catalogue/" + pack.getSlug();
						XSSFHyperlink link = (XSSFHyperlink)workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
					    link.setAddress(url);
					    dataCell.setCellValue(url);
					    dataCell.setHyperlink((XSSFHyperlink) link);

						
						dataCell = dataRow.createCell(5);
						dataCell.setCellValue(downloadedLibrary.contains(pack.getUuid()));
						
						dataCell = dataRow.createCell(6);
						dataCell.setCellValue(downloadedOnDevice.contains(pack.getUuid()));
						
						dataCell = dataRow.createCell(7);
						dataCell.setCellValue(usedLocale);
						
					});
					
					for(int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
						sheet.autoSizeColumn(i);
					}
					
					FileOutputStream fos;
					try {
						fos = new FileOutputStream(chooser.getSelectedFile());
						workbook.write(fos);
						fos.close();
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					} catch (IOException e1) {
						e1.printStackTrace();
					} finally {
						try {
							workbook.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
					

				}
				
			}
			
		});
		mnNewMenu.add(exportMenuItem);
		mntmNewMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.META_DOWN_MASK));
		mnNewMenu.add(mntmNewMenuItem);

	}


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
				indexString = (Object) transferable.getTransferData(DataFlavor.stringFlavor);
			} catch (Exception e) {
				return false;
			}

			List<Integer> indices = Arrays.stream(indexString.toString().split(",")).mapToInt(Integer::parseInt).boxed().toList();
			JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
			final Integer dropTargetIndex = dl.getIndex();

			int nbBeforeDropIndex = (int) indices.stream().filter((v) -> v < dropTargetIndex.intValue()).count();

			List<T> objects = new ArrayList<T>();
			for (int index = indices.size() - 1; index >= 0; index--) {
				objects.add(((DefaultListModel<T>) list.getModel()).remove(indices.get(index)));
			}

			int insertIndex = dropTargetIndex - nbBeforeDropIndex;

			for (int i = objects.size() - 1; i >= 0; i--) {
				((DefaultListModel<T>) list.getModel()).add(insertIndex++, objects.get(i));

			}

			return true;
		}
	}
}
