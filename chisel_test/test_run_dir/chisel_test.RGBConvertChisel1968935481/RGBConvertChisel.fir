;buildInfoPackage: chisel3, version: 3.0-SNAPSHOT, scalaVersion: 2.11.7, sbtVersion: 0.13.12, builtAtString: 2017-03-24 17:25:51.022, builtAtMillis: 1490376351022
circuit RGBConvertChisel : 
  module RGBConvertChisel : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip stream_in_data : UInt<24>, flip stream_in_startofpacket : UInt<1>, flip stream_in_endofpacket : UInt<1>, flip stream_in_empty : UInt<2>, flip stream_in_valid : UInt<1>, flip stream_out_ready : UInt<1>, stream_in_ready : UInt<1>, stream_out_data : UInt<16>, stream_out_startofpacket : UInt<1>, stream_out_endofpacket : UInt<1>, stream_out_empty : UInt<1>, stream_out_valid : UInt<1>}
    
    io is invalid
    io is invalid
    node _T_26 = not(io.stream_out_valid) @[RGBConvertChisel.scala 26:47]
    node _T_27 = or(io.stream_out_ready, _T_26) @[RGBConvertChisel.scala 26:45]
    io.stream_in_ready <= _T_27 @[RGBConvertChisel.scala 26:22]
    node _T_28 = bits(io.stream_in_data, 23, 16) @[RGBConvertChisel.scala 27:32]
    node _T_29 = bits(io.stream_in_data, 23, 22) @[RGBConvertChisel.scala 27:59]
    node r = cat(_T_28, _T_29) @[Cat.scala 30:58]
    node _T_30 = bits(io.stream_in_data, 15, 8) @[RGBConvertChisel.scala 28:32]
    node _T_31 = bits(io.stream_in_data, 15, 14) @[RGBConvertChisel.scala 28:58]
    node g = cat(_T_30, _T_31) @[Cat.scala 30:58]
    node _T_32 = bits(io.stream_in_data, 7, 0) @[RGBConvertChisel.scala 29:32]
    node _T_33 = bits(io.stream_in_data, 7, 6) @[RGBConvertChisel.scala 29:57]
    node b = cat(_T_32, _T_33) @[Cat.scala 30:58]
    node _T_34 = bits(r, 9, 5) @[RGBConvertChisel.scala 30:29]
    node _T_35 = bits(g, 9, 4) @[RGBConvertChisel.scala 30:38]
    node _T_36 = bits(b, 9, 5) @[RGBConvertChisel.scala 30:47]
    node _T_37 = cat(_T_34, _T_35) @[Cat.scala 30:58]
    node converted_data = cat(_T_37, _T_36) @[Cat.scala 30:58]
    when reset : @[RGBConvertChisel.scala 33:16]
      io.stream_out_data <= UInt<1>("h00") @[RGBConvertChisel.scala 34:34]
      io.stream_out_startofpacket <= UInt<1>("h00") @[RGBConvertChisel.scala 35:34]
      io.stream_out_endofpacket <= UInt<1>("h00") @[RGBConvertChisel.scala 36:34]
      io.stream_out_empty <= UInt<1>("h00") @[RGBConvertChisel.scala 37:34]
      io.stream_out_valid <= UInt<1>("h00") @[RGBConvertChisel.scala 38:34]
      skip @[RGBConvertChisel.scala 33:16]
    node _T_43 = not(io.stream_out_valid) @[RGBConvertChisel.scala 39:38]
    node _T_44 = or(io.stream_out_ready, _T_43) @[RGBConvertChisel.scala 39:36]
    node _T_46 = eq(reset, UInt<1>("h00")) @[RGBConvertChisel.scala 33:16]
    node _T_47 = and(_T_46, _T_44) @[RGBConvertChisel.scala 39:60]
    when _T_47 : @[RGBConvertChisel.scala 39:60]
      io.stream_out_data <= converted_data @[RGBConvertChisel.scala 40:34]
      io.stream_out_startofpacket <= io.stream_in_startofpacket @[RGBConvertChisel.scala 41:34]
      io.stream_out_endofpacket <= io.stream_in_endofpacket @[RGBConvertChisel.scala 42:34]
      io.stream_out_empty <= io.stream_in_empty @[RGBConvertChisel.scala 43:34]
      io.stream_out_valid <= io.stream_in_valid @[RGBConvertChisel.scala 44:34]
      skip @[RGBConvertChisel.scala 39:60]
    
