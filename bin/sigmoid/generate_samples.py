def generate_samples(lo, up, n_samples, n_decimals):
    list = []
    prec = (float(up) - float(lo)) / n_samples
    print("prec = ", (prec))
    for i in iter(range(n_samples)):
        list.append(i * prec + lo)
    decimal_str = "%." + str(n_decimals) + "f"
    re_list = [decimal_str % v for v in list]
    return re_list

if __name__ == "__main__":
    sample_list = generate_samples(-32., 32., 128, 4)
    for v in sample_list:
        print(v)
