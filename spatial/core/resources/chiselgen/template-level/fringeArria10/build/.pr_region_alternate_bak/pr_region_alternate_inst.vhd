	component pr_region_alternate is
		port (
			clk_clk            : in  std_logic                      := 'X';             -- clk
			io_m_axi_0_awid    : out std_logic_vector(5 downto 0);                      -- awid
			io_m_axi_0_awuser  : out std_logic_vector(31 downto 0);                     -- awuser
			io_m_axi_0_awaddr  : out std_logic_vector(31 downto 0);                     -- awaddr
			io_m_axi_0_awlen   : out std_logic_vector(7 downto 0);                      -- awlen
			io_m_axi_0_awsize  : out std_logic_vector(2 downto 0);                      -- awsize
			io_m_axi_0_awburst : out std_logic_vector(1 downto 0);                      -- awburst
			io_m_axi_0_awlock  : out std_logic;                                         -- awlock
			io_m_axi_0_awcache : out std_logic_vector(3 downto 0);                      -- awcache
			io_m_axi_0_awprot  : out std_logic_vector(2 downto 0);                      -- awprot
			io_m_axi_0_awqos   : out std_logic_vector(3 downto 0);                      -- awqos
			io_m_axi_0_awvalid : out std_logic;                                         -- awvalid
			io_m_axi_0_awready : in  std_logic                      := 'X';             -- awready
			io_m_axi_0_arid    : out std_logic_vector(5 downto 0);                      -- arid
			io_m_axi_0_aruser  : out std_logic_vector(31 downto 0);                     -- aruser
			io_m_axi_0_araddr  : out std_logic_vector(31 downto 0);                     -- araddr
			io_m_axi_0_arlen   : out std_logic_vector(7 downto 0);                      -- arlen
			io_m_axi_0_arsize  : out std_logic_vector(2 downto 0);                      -- arsize
			io_m_axi_0_arburst : out std_logic_vector(1 downto 0);                      -- arburst
			io_m_axi_0_arlock  : out std_logic;                                         -- arlock
			io_m_axi_0_arcache : out std_logic_vector(3 downto 0);                      -- arcache
			io_m_axi_0_arprot  : out std_logic_vector(2 downto 0);                      -- arprot
			io_m_axi_0_arqos   : out std_logic_vector(3 downto 0);                      -- arqos
			io_m_axi_0_arvalid : out std_logic;                                         -- arvalid
			io_m_axi_0_arready : in  std_logic                      := 'X';             -- arready
			io_m_axi_0_wdata   : out std_logic_vector(511 downto 0);                    -- wdata
			io_m_axi_0_wstrb   : out std_logic_vector(63 downto 0);                     -- wstrb
			io_m_axi_0_wlast   : out std_logic;                                         -- wlast
			io_m_axi_0_wvalid  : out std_logic;                                         -- wvalid
			io_m_axi_0_wready  : in  std_logic                      := 'X';             -- wready
			io_m_axi_0_rid     : in  std_logic_vector(5 downto 0)   := (others => 'X'); -- rid
			io_m_axi_0_ruser   : in  std_logic_vector(31 downto 0)  := (others => 'X'); -- ruser
			io_m_axi_0_rdata   : in  std_logic_vector(511 downto 0) := (others => 'X'); -- rdata
			io_m_axi_0_rresp   : in  std_logic_vector(1 downto 0)   := (others => 'X'); -- rresp
			io_m_axi_0_rlast   : in  std_logic                      := 'X';             -- rlast
			io_m_axi_0_rvalid  : in  std_logic                      := 'X';             -- rvalid
			io_m_axi_0_rready  : out std_logic;                                         -- rready
			io_m_axi_0_bid     : in  std_logic_vector(5 downto 0)   := (others => 'X'); -- bid
			io_m_axi_0_buser   : in  std_logic_vector(31 downto 0)  := (others => 'X'); -- buser
			io_m_axi_0_bresp   : in  std_logic_vector(1 downto 0)   := (others => 'X'); -- bresp
			io_m_axi_0_bvalid  : in  std_logic                      := 'X';             -- bvalid
			io_m_axi_0_bready  : out std_logic;                                         -- bready
			reset_reset        : in  std_logic                      := 'X';             -- reset
			s0_waitrequest     : out std_logic;                                         -- waitrequest
			s0_readdata        : out std_logic_vector(31 downto 0);                     -- readdata
			s0_readdatavalid   : out std_logic;                                         -- readdatavalid
			s0_burstcount      : in  std_logic_vector(0 downto 0)   := (others => 'X'); -- burstcount
			s0_writedata       : in  std_logic_vector(31 downto 0)  := (others => 'X'); -- writedata
			s0_address         : in  std_logic_vector(9 downto 0)   := (others => 'X'); -- address
			s0_write           : in  std_logic                      := 'X';             -- write
			s0_read            : in  std_logic                      := 'X';             -- read
			s0_byteenable      : in  std_logic_vector(3 downto 0)   := (others => 'X'); -- byteenable
			s0_debugaccess     : in  std_logic                      := 'X'              -- debugaccess
		);
	end component pr_region_alternate;

	u0 : component pr_region_alternate
		port map (
			clk_clk            => CONNECTED_TO_clk_clk,            --        clk.clk
			io_m_axi_0_awid    => CONNECTED_TO_io_m_axi_0_awid,    -- io_m_axi_0.awid
			io_m_axi_0_awuser  => CONNECTED_TO_io_m_axi_0_awuser,  --           .awuser
			io_m_axi_0_awaddr  => CONNECTED_TO_io_m_axi_0_awaddr,  --           .awaddr
			io_m_axi_0_awlen   => CONNECTED_TO_io_m_axi_0_awlen,   --           .awlen
			io_m_axi_0_awsize  => CONNECTED_TO_io_m_axi_0_awsize,  --           .awsize
			io_m_axi_0_awburst => CONNECTED_TO_io_m_axi_0_awburst, --           .awburst
			io_m_axi_0_awlock  => CONNECTED_TO_io_m_axi_0_awlock,  --           .awlock
			io_m_axi_0_awcache => CONNECTED_TO_io_m_axi_0_awcache, --           .awcache
			io_m_axi_0_awprot  => CONNECTED_TO_io_m_axi_0_awprot,  --           .awprot
			io_m_axi_0_awqos   => CONNECTED_TO_io_m_axi_0_awqos,   --           .awqos
			io_m_axi_0_awvalid => CONNECTED_TO_io_m_axi_0_awvalid, --           .awvalid
			io_m_axi_0_awready => CONNECTED_TO_io_m_axi_0_awready, --           .awready
			io_m_axi_0_arid    => CONNECTED_TO_io_m_axi_0_arid,    --           .arid
			io_m_axi_0_aruser  => CONNECTED_TO_io_m_axi_0_aruser,  --           .aruser
			io_m_axi_0_araddr  => CONNECTED_TO_io_m_axi_0_araddr,  --           .araddr
			io_m_axi_0_arlen   => CONNECTED_TO_io_m_axi_0_arlen,   --           .arlen
			io_m_axi_0_arsize  => CONNECTED_TO_io_m_axi_0_arsize,  --           .arsize
			io_m_axi_0_arburst => CONNECTED_TO_io_m_axi_0_arburst, --           .arburst
			io_m_axi_0_arlock  => CONNECTED_TO_io_m_axi_0_arlock,  --           .arlock
			io_m_axi_0_arcache => CONNECTED_TO_io_m_axi_0_arcache, --           .arcache
			io_m_axi_0_arprot  => CONNECTED_TO_io_m_axi_0_arprot,  --           .arprot
			io_m_axi_0_arqos   => CONNECTED_TO_io_m_axi_0_arqos,   --           .arqos
			io_m_axi_0_arvalid => CONNECTED_TO_io_m_axi_0_arvalid, --           .arvalid
			io_m_axi_0_arready => CONNECTED_TO_io_m_axi_0_arready, --           .arready
			io_m_axi_0_wdata   => CONNECTED_TO_io_m_axi_0_wdata,   --           .wdata
			io_m_axi_0_wstrb   => CONNECTED_TO_io_m_axi_0_wstrb,   --           .wstrb
			io_m_axi_0_wlast   => CONNECTED_TO_io_m_axi_0_wlast,   --           .wlast
			io_m_axi_0_wvalid  => CONNECTED_TO_io_m_axi_0_wvalid,  --           .wvalid
			io_m_axi_0_wready  => CONNECTED_TO_io_m_axi_0_wready,  --           .wready
			io_m_axi_0_rid     => CONNECTED_TO_io_m_axi_0_rid,     --           .rid
			io_m_axi_0_ruser   => CONNECTED_TO_io_m_axi_0_ruser,   --           .ruser
			io_m_axi_0_rdata   => CONNECTED_TO_io_m_axi_0_rdata,   --           .rdata
			io_m_axi_0_rresp   => CONNECTED_TO_io_m_axi_0_rresp,   --           .rresp
			io_m_axi_0_rlast   => CONNECTED_TO_io_m_axi_0_rlast,   --           .rlast
			io_m_axi_0_rvalid  => CONNECTED_TO_io_m_axi_0_rvalid,  --           .rvalid
			io_m_axi_0_rready  => CONNECTED_TO_io_m_axi_0_rready,  --           .rready
			io_m_axi_0_bid     => CONNECTED_TO_io_m_axi_0_bid,     --           .bid
			io_m_axi_0_buser   => CONNECTED_TO_io_m_axi_0_buser,   --           .buser
			io_m_axi_0_bresp   => CONNECTED_TO_io_m_axi_0_bresp,   --           .bresp
			io_m_axi_0_bvalid  => CONNECTED_TO_io_m_axi_0_bvalid,  --           .bvalid
			io_m_axi_0_bready  => CONNECTED_TO_io_m_axi_0_bready,  --           .bready
			reset_reset        => CONNECTED_TO_reset_reset,        --      reset.reset
			s0_waitrequest     => CONNECTED_TO_s0_waitrequest,     --         s0.waitrequest
			s0_readdata        => CONNECTED_TO_s0_readdata,        --           .readdata
			s0_readdatavalid   => CONNECTED_TO_s0_readdatavalid,   --           .readdatavalid
			s0_burstcount      => CONNECTED_TO_s0_burstcount,      --           .burstcount
			s0_writedata       => CONNECTED_TO_s0_writedata,       --           .writedata
			s0_address         => CONNECTED_TO_s0_address,         --           .address
			s0_write           => CONNECTED_TO_s0_write,           --           .write
			s0_read            => CONNECTED_TO_s0_read,            --           .read
			s0_byteenable      => CONNECTED_TO_s0_byteenable,      --           .byteenable
			s0_debugaccess     => CONNECTED_TO_s0_debugaccess      --           .debugaccess
		);

