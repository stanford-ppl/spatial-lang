;buildInfoPackage: chisel3, version: 3.0-SNAPSHOT, scalaVersion: 2.11.7, sbtVersion: 0.13.12, builtAtString: 2017-03-24 17:25:51.022, builtAtMillis: 1490376351022
circuit Counter : 
  module FF : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip input : {data : UInt<32>, init : UInt<32>, enable : UInt<1>, reset : UInt<1>}, output : {data : UInt<32>}}
    
    io is invalid
    io is invalid
    reg ff : UInt, clock with : (reset => (reset, io.input.init)) @[FF.scala 32:19]
    node _T_17 = mux(io.input.enable, io.input.data, ff) @[FF.scala 33:47]
    node _T_18 = mux(io.input.reset, io.input.init, _T_17) @[FF.scala 33:12]
    ff <= _T_18 @[FF.scala 33:6]
    node _T_19 = mux(io.input.reset, io.input.init, ff) @[FF.scala 34:24]
    io.output.data <= _T_19 @[FF.scala 34:18]
    
  module SingleCounter : 
    input clock : Clock
    input reset : UInt<1>
    output io : {input : {flip start : UInt<32>, flip max : UInt<32>, flip stride : UInt<32>, flip gap : UInt<32>, flip reset : UInt<1>, flip enable : UInt<1>, flip saturate : UInt<1>}, output : {count : UInt<32>[1], countWithoutWrap : UInt<32>[1], done : UInt<1>, extendedDone : UInt<1>, saturated : UInt<1>}}
    
    io is invalid
    io is invalid
    inst FF of FF @[Counter.scala 124:22]
    FF.io is invalid
    FF.clock <= clock
    FF.reset <= reset
    FF.io.input.init <= io.input.start @[Counter.scala 126:24]
    FF.io.input.reset <= io.input.reset @[Counter.scala 127:25]
    node _T_32 = or(io.input.reset, io.input.enable) @[Counter.scala 128:44]
    FF.io.input.enable <= _T_32 @[Counter.scala 128:26]
    node _T_34 = mul(io.input.stride, UInt<1>("h01")) @[Counter.scala 131:43]
    node _T_35 = add(FF.io.output.data, _T_34) @[Counter.scala 131:24]
    node _T_36 = tail(_T_35, 1) @[Counter.scala 131:24]
    node _T_37 = add(_T_36, io.input.gap) @[Counter.scala 131:52]
    node _T_38 = tail(_T_37, 1) @[Counter.scala 131:52]
    node _T_39 = geq(_T_38, io.input.max) @[Counter.scala 132:24]
    reg _T_42 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 133:25]
    _T_42 <= _T_39 @[Counter.scala 133:25]
    reg _T_45 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 134:29]
    _T_45 <= io.input.enable @[Counter.scala 134:29]
    node _T_46 = mux(io.input.saturate, FF.io.output.data, io.input.start) @[Counter.scala 135:30]
    node _T_47 = mux(_T_39, _T_46, _T_38) @[Counter.scala 135:19]
    node _T_48 = mux(io.input.reset, io.input.start, _T_47) @[Counter.scala 136:30]
    FF.io.input.data <= _T_48 @[Counter.scala 136:24]
    node _T_50 = mul(UInt<1>("h00"), io.input.stride) @[Counter.scala 138:67]
    node _T_51 = add(FF.io.output.data, _T_50) @[Counter.scala 138:62]
    node _T_52 = tail(_T_51, 1) @[Counter.scala 138:62]
    io.output.count[0] <= _T_52 @[Counter.scala 138:53]
    node _T_54 = eq(FF.io.output.data, UInt<1>("h00")) @[Counter.scala 140:50]
    node _T_55 = mux(_T_54, io.input.max, FF.io.output.data) @[Counter.scala 140:43]
    node _T_57 = mul(UInt<1>("h00"), io.input.stride) @[Counter.scala 140:85]
    node _T_58 = add(_T_55, _T_57) @[Counter.scala 140:80]
    node _T_59 = tail(_T_58, 1) @[Counter.scala 140:80]
    io.output.countWithoutWrap[0] <= _T_59 @[Counter.scala 140:37]
    node _T_60 = and(io.input.enable, _T_39) @[Counter.scala 142:39]
    io.output.done <= _T_60 @[Counter.scala 142:20]
    node _T_61 = and(io.input.saturate, _T_39) @[Counter.scala 143:46]
    io.output.saturated <= _T_61 @[Counter.scala 143:25]
    node _T_62 = or(io.input.enable, _T_45) @[Counter.scala 144:48]
    node _T_63 = or(_T_39, _T_42) @[Counter.scala 144:71]
    node _T_64 = and(_T_62, _T_63) @[Counter.scala 144:62]
    io.output.extendedDone <= _T_64 @[Counter.scala 144:28]
    
  module FF_1 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip input : {data : UInt<32>, init : UInt<32>, enable : UInt<1>, reset : UInt<1>}, output : {data : UInt<32>}}
    
    io is invalid
    io is invalid
    reg ff : UInt, clock with : (reset => (reset, io.input.init)) @[FF.scala 32:19]
    node _T_17 = mux(io.input.enable, io.input.data, ff) @[FF.scala 33:47]
    node _T_18 = mux(io.input.reset, io.input.init, _T_17) @[FF.scala 33:12]
    ff <= _T_18 @[FF.scala 33:6]
    node _T_19 = mux(io.input.reset, io.input.init, ff) @[FF.scala 34:24]
    io.output.data <= _T_19 @[FF.scala 34:18]
    
  module SingleCounter_1 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {input : {flip start : UInt<32>, flip max : UInt<32>, flip stride : UInt<32>, flip gap : UInt<32>, flip reset : UInt<1>, flip enable : UInt<1>, flip saturate : UInt<1>}, output : {count : UInt<32>[1], countWithoutWrap : UInt<32>[1], done : UInt<1>, extendedDone : UInt<1>, saturated : UInt<1>}}
    
    io is invalid
    io is invalid
    inst FF of FF_1 @[Counter.scala 124:22]
    FF.io is invalid
    FF.clock <= clock
    FF.reset <= reset
    FF.io.input.init <= io.input.start @[Counter.scala 126:24]
    FF.io.input.reset <= io.input.reset @[Counter.scala 127:25]
    node _T_32 = or(io.input.reset, io.input.enable) @[Counter.scala 128:44]
    FF.io.input.enable <= _T_32 @[Counter.scala 128:26]
    node _T_34 = mul(io.input.stride, UInt<1>("h01")) @[Counter.scala 131:43]
    node _T_35 = add(FF.io.output.data, _T_34) @[Counter.scala 131:24]
    node _T_36 = tail(_T_35, 1) @[Counter.scala 131:24]
    node _T_37 = add(_T_36, io.input.gap) @[Counter.scala 131:52]
    node _T_38 = tail(_T_37, 1) @[Counter.scala 131:52]
    node _T_39 = geq(_T_38, io.input.max) @[Counter.scala 132:24]
    reg _T_42 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 133:25]
    _T_42 <= _T_39 @[Counter.scala 133:25]
    reg _T_45 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 134:29]
    _T_45 <= io.input.enable @[Counter.scala 134:29]
    node _T_46 = mux(io.input.saturate, FF.io.output.data, io.input.start) @[Counter.scala 135:30]
    node _T_47 = mux(_T_39, _T_46, _T_38) @[Counter.scala 135:19]
    node _T_48 = mux(io.input.reset, io.input.start, _T_47) @[Counter.scala 136:30]
    FF.io.input.data <= _T_48 @[Counter.scala 136:24]
    node _T_50 = mul(UInt<1>("h00"), io.input.stride) @[Counter.scala 138:67]
    node _T_51 = add(FF.io.output.data, _T_50) @[Counter.scala 138:62]
    node _T_52 = tail(_T_51, 1) @[Counter.scala 138:62]
    io.output.count[0] <= _T_52 @[Counter.scala 138:53]
    node _T_54 = eq(FF.io.output.data, UInt<1>("h00")) @[Counter.scala 140:50]
    node _T_55 = mux(_T_54, io.input.max, FF.io.output.data) @[Counter.scala 140:43]
    node _T_57 = mul(UInt<1>("h00"), io.input.stride) @[Counter.scala 140:85]
    node _T_58 = add(_T_55, _T_57) @[Counter.scala 140:80]
    node _T_59 = tail(_T_58, 1) @[Counter.scala 140:80]
    io.output.countWithoutWrap[0] <= _T_59 @[Counter.scala 140:37]
    node _T_60 = and(io.input.enable, _T_39) @[Counter.scala 142:39]
    io.output.done <= _T_60 @[Counter.scala 142:20]
    node _T_61 = and(io.input.saturate, _T_39) @[Counter.scala 143:46]
    io.output.saturated <= _T_61 @[Counter.scala 143:25]
    node _T_62 = or(io.input.enable, _T_45) @[Counter.scala 144:48]
    node _T_63 = or(_T_39, _T_42) @[Counter.scala 144:71]
    node _T_64 = and(_T_62, _T_63) @[Counter.scala 144:62]
    io.output.extendedDone <= _T_64 @[Counter.scala 144:28]
    
  module FF_2 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip input : {data : UInt<32>, init : UInt<32>, enable : UInt<1>, reset : UInt<1>}, output : {data : UInt<32>}}
    
    io is invalid
    io is invalid
    reg ff : UInt, clock with : (reset => (reset, io.input.init)) @[FF.scala 32:19]
    node _T_17 = mux(io.input.enable, io.input.data, ff) @[FF.scala 33:47]
    node _T_18 = mux(io.input.reset, io.input.init, _T_17) @[FF.scala 33:12]
    ff <= _T_18 @[FF.scala 33:6]
    node _T_19 = mux(io.input.reset, io.input.init, ff) @[FF.scala 34:24]
    io.output.data <= _T_19 @[FF.scala 34:18]
    
  module SingleCounter_2 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {input : {flip start : UInt<32>, flip max : UInt<32>, flip stride : UInt<32>, flip gap : UInt<32>, flip reset : UInt<1>, flip enable : UInt<1>, flip saturate : UInt<1>}, output : {count : UInt<32>[1], countWithoutWrap : UInt<32>[1], done : UInt<1>, extendedDone : UInt<1>, saturated : UInt<1>}}
    
    io is invalid
    io is invalid
    inst FF of FF_2 @[Counter.scala 124:22]
    FF.io is invalid
    FF.clock <= clock
    FF.reset <= reset
    FF.io.input.init <= io.input.start @[Counter.scala 126:24]
    FF.io.input.reset <= io.input.reset @[Counter.scala 127:25]
    node _T_32 = or(io.input.reset, io.input.enable) @[Counter.scala 128:44]
    FF.io.input.enable <= _T_32 @[Counter.scala 128:26]
    node _T_34 = mul(io.input.stride, UInt<1>("h01")) @[Counter.scala 131:43]
    node _T_35 = add(FF.io.output.data, _T_34) @[Counter.scala 131:24]
    node _T_36 = tail(_T_35, 1) @[Counter.scala 131:24]
    node _T_37 = add(_T_36, io.input.gap) @[Counter.scala 131:52]
    node _T_38 = tail(_T_37, 1) @[Counter.scala 131:52]
    node _T_39 = geq(_T_38, io.input.max) @[Counter.scala 132:24]
    reg _T_42 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 133:25]
    _T_42 <= _T_39 @[Counter.scala 133:25]
    reg _T_45 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 134:29]
    _T_45 <= io.input.enable @[Counter.scala 134:29]
    node _T_46 = mux(io.input.saturate, FF.io.output.data, io.input.start) @[Counter.scala 135:30]
    node _T_47 = mux(_T_39, _T_46, _T_38) @[Counter.scala 135:19]
    node _T_48 = mux(io.input.reset, io.input.start, _T_47) @[Counter.scala 136:30]
    FF.io.input.data <= _T_48 @[Counter.scala 136:24]
    node _T_50 = mul(UInt<1>("h00"), io.input.stride) @[Counter.scala 138:67]
    node _T_51 = add(FF.io.output.data, _T_50) @[Counter.scala 138:62]
    node _T_52 = tail(_T_51, 1) @[Counter.scala 138:62]
    io.output.count[0] <= _T_52 @[Counter.scala 138:53]
    node _T_54 = eq(FF.io.output.data, UInt<1>("h00")) @[Counter.scala 140:50]
    node _T_55 = mux(_T_54, io.input.max, FF.io.output.data) @[Counter.scala 140:43]
    node _T_57 = mul(UInt<1>("h00"), io.input.stride) @[Counter.scala 140:85]
    node _T_58 = add(_T_55, _T_57) @[Counter.scala 140:80]
    node _T_59 = tail(_T_58, 1) @[Counter.scala 140:80]
    io.output.countWithoutWrap[0] <= _T_59 @[Counter.scala 140:37]
    node _T_60 = and(io.input.enable, _T_39) @[Counter.scala 142:39]
    io.output.done <= _T_60 @[Counter.scala 142:20]
    node _T_61 = and(io.input.saturate, _T_39) @[Counter.scala 143:46]
    io.output.saturated <= _T_61 @[Counter.scala 143:25]
    node _T_62 = or(io.input.enable, _T_45) @[Counter.scala 144:48]
    node _T_63 = or(_T_39, _T_42) @[Counter.scala 144:71]
    node _T_64 = and(_T_62, _T_63) @[Counter.scala 144:62]
    io.output.extendedDone <= _T_64 @[Counter.scala 144:28]
    
  module Counter : 
    input clock : Clock
    input reset : UInt<1>
    output io : {input : {flip starts : UInt<32>[3], flip maxes : UInt<32>[3], flip strides : UInt<32>[3], flip gaps : UInt<32>[3], flip reset : UInt<1>, flip enable : UInt<1>, flip saturate : UInt<1>, flip isStream : UInt<1>}, output : {counts : UInt<32>[3], done : UInt<1>, extendedDone : UInt<1>, saturated : UInt<1>}}
    
    io is invalid
    io is invalid
    inst ctrs_0 of SingleCounter @[Counter.scala 194:46]
    ctrs_0.io is invalid
    ctrs_0.clock <= clock
    ctrs_0.reset <= reset
    inst ctrs_1 of SingleCounter_1 @[Counter.scala 194:46]
    ctrs_1.io is invalid
    ctrs_1.clock <= clock
    ctrs_1.reset <= reset
    inst ctrs_2 of SingleCounter_2 @[Counter.scala 194:46]
    ctrs_2.io is invalid
    ctrs_2.clock <= clock
    ctrs_2.reset <= reset
    ctrs_0.io.input.start <= io.input.starts[0] @[Counter.scala 198:24]
    ctrs_0.io.input.max <= io.input.maxes[0] @[Counter.scala 199:22]
    ctrs_0.io.input.stride <= io.input.strides[0] @[Counter.scala 200:25]
    ctrs_0.io.input.gap <= io.input.gaps[0] @[Counter.scala 201:22]
    ctrs_0.io.input.reset <= io.input.reset @[Counter.scala 202:24]
    ctrs_0.io.input.gap <= UInt<1>("h00") @[Counter.scala 203:22]
    ctrs_1.io.input.start <= io.input.starts[1] @[Counter.scala 198:24]
    ctrs_1.io.input.max <= io.input.maxes[1] @[Counter.scala 199:22]
    ctrs_1.io.input.stride <= io.input.strides[1] @[Counter.scala 200:25]
    ctrs_1.io.input.gap <= io.input.gaps[1] @[Counter.scala 201:22]
    ctrs_1.io.input.reset <= io.input.reset @[Counter.scala 202:24]
    ctrs_1.io.input.gap <= UInt<1>("h00") @[Counter.scala 203:22]
    ctrs_2.io.input.start <= io.input.starts[2] @[Counter.scala 198:24]
    ctrs_2.io.input.max <= io.input.maxes[2] @[Counter.scala 199:22]
    ctrs_2.io.input.stride <= io.input.strides[2] @[Counter.scala 200:25]
    ctrs_2.io.input.gap <= io.input.gaps[2] @[Counter.scala 201:22]
    ctrs_2.io.input.reset <= io.input.reset @[Counter.scala 202:24]
    ctrs_2.io.input.gap <= UInt<1>("h00") @[Counter.scala 203:22]
    ctrs_2.io.input.enable <= io.input.enable @[Counter.scala 207:33]
    node _T_41 = and(ctrs_1.io.output.done, io.input.enable) @[Counter.scala 209:57]
    ctrs_0.io.input.enable <= _T_41 @[Counter.scala 209:29]
    node _T_42 = and(ctrs_2.io.output.done, io.input.enable) @[Counter.scala 209:57]
    ctrs_1.io.input.enable <= _T_42 @[Counter.scala 209:29]
    ctrs_0.io.input.saturate <= io.input.saturate @[Counter.scala 213:29]
    node _T_43 = and(io.input.saturate, ctrs_0.io.output.saturated) @[Counter.scala 215:52]
    ctrs_1.io.input.saturate <= _T_43 @[Counter.scala 215:31]
    node _T_44 = and(ctrs_0.io.output.saturated, ctrs_1.io.output.saturated) @[Counter.scala 215:113]
    node _T_45 = and(io.input.saturate, _T_44) @[Counter.scala 215:52]
    ctrs_2.io.input.saturate <= _T_45 @[Counter.scala 215:31]
    io.output.counts[0] <= ctrs_0.io.output.count[0] @[Counter.scala 221:57]
    io.output.counts[1] <= ctrs_1.io.output.count[0] @[Counter.scala 221:57]
    io.output.counts[2] <= ctrs_2.io.output.count[0] @[Counter.scala 221:57]
    node _T_46 = and(ctrs_0.io.output.done, ctrs_1.io.output.done) @[Counter.scala 230:51]
    node isDone = and(_T_46, ctrs_2.io.output.done) @[Counter.scala 230:51]
    reg wasDone : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 231:24]
    wasDone <= isDone @[Counter.scala 231:24]
    node _T_49 = and(ctrs_0.io.output.saturated, ctrs_1.io.output.saturated) @[Counter.scala 232:61]
    node isSaturated = and(_T_49, ctrs_2.io.output.saturated) @[Counter.scala 232:61]
    reg wasWasDone : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[Counter.scala 233:27]
    wasWasDone <= wasDone @[Counter.scala 233:27]
    node _T_53 = mux(io.input.isStream, UInt<1>("h01"), io.input.enable) @[Counter.scala 234:24]
    node _T_54 = and(_T_53, isDone) @[Counter.scala 234:69]
    node _T_55 = not(wasDone) @[Counter.scala 234:80]
    node _T_56 = and(_T_54, _T_55) @[Counter.scala 234:78]
    io.output.done <= _T_56 @[Counter.scala 234:18]
    node _T_57 = and(io.input.enable, isDone) @[Counter.scala 235:45]
    node _T_58 = not(wasWasDone) @[Counter.scala 235:56]
    node _T_59 = and(_T_57, _T_58) @[Counter.scala 235:54]
    io.output.extendedDone <= _T_59 @[Counter.scala 235:26]
    node _T_60 = and(io.input.saturate, isSaturated) @[Counter.scala 236:44]
    io.output.saturated <= _T_60 @[Counter.scala 236:23]
    
